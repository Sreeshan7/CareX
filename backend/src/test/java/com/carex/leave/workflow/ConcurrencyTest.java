package com.carex.leave.workflow;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.dashboard.DashboardService;
import com.carex.leave.leave.balance.LeaveBalanceRepository;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.support.IntegrationTest;
import com.carex.leave.workflow.LeaveWorkflowService.Decision;
import com.carex.leave.workflow.LeaveWorkflowService.DecisionCommand;
import com.carex.leave.workflow.LeaveWorkflowService.SubmitCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Real race conditions against real PostgreSQL (implementation.md §12, §21). */
class ConcurrencyTest extends IntegrationTest {
    @Autowired LeaveWorkflowService workflow;
    @Autowired EscalationJob escalationJob;
    @Autowired LeaveRequestRepository requests;
    @Autowired LeaveBalanceRepository balances;
    @Autowired DashboardService dashboard;

    final ExecutorService pool = Executors.newFixedThreadPool(12);

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    /** Runs all tasks at the same instant (start gate) and returns outcome objects (value or exception). */
    List<Object> race(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Object>> fs = new ArrayList<>();
        for (Callable<Object> t : tasks) {
            fs.add(pool.submit(() -> {
                gate.await();
                try {
                    return t.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        gate.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : fs) out.add(f.get(60, TimeUnit.SECONDS));
        return out;
    }

    static String code(Object o) {
        return o instanceof ApiException e ? e.getCode() : o instanceof Exception e ? e.getClass().getSimpleName() : "OK";
    }

    @Test
    void simultaneousBalanceConsumingRequestsCannotOverdraw() throws Exception {
        // 10 non-overlapping 3-day CASUAL requests, only 8 days available → exactly 2 succeed
        String[] mondays = {"2026-10-05", "2026-10-12", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23",
                "2026-11-30", "2026-12-07", "2026-12-14", "2026-12-21"};
        List<Callable<Object>> tasks = new ArrayList<>();
        for (String m : mondays) {
            LocalDate s = LocalDate.parse(m);
            tasks.add(() -> workflow.submit(org.arjun, new SubmitCommand("CASUAL", s, s.plusDays(2), "r", UUID.randomUUID())));
        }
        List<Object> results = race(tasks);
        long ok = results.stream().filter(r -> code(r).equals("OK")).count();
        long insufficient = results.stream().filter(r -> code(r).equals("INSUFFICIENT_BALANCE")).count();
        assertThat(ok).isEqualTo(2);
        assertThat(insufficient).isEqualTo(8);
        var b = balances.findByUserIdAndLeaveTypeCodeAndYear(org.arjun.id(), "CASUAL", 2026).orElseThrow();
        assertThat(b.getPendingDays()).isEqualByComparingTo("6.0");
        assertThat(b.available()).isEqualByComparingTo("2.0");
        assertThat(dashboard.balanceIntegrity()).isEmpty();
    }

    @Test
    void duplicateConcurrentSubmissionsCreateOneRequest() throws Exception {
        UUID key = UUID.randomUUID();
        var cmd = new SubmitCommand("CASUAL", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), "r", key);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> workflow.submit(org.arjun, cmd).request().getId());
        List<Object> results = race(tasks);
        assertThat(results).allMatch(r -> r instanceof Long);
        assertThat(results.stream().distinct().count()).isEqualTo(1);
        assertThat(requests.count()).isEqualTo(1);
        assertThat(balances.findByUserIdAndLeaveTypeCodeAndYear(org.arjun.id(), "CASUAL", 2026).orElseThrow()
                .getPendingDays()).isEqualByComparingTo("3.0");
    }

    @Test
    void twoSimultaneousApprovalsYieldExactlyOneDecision() throws Exception {
        Long id = workflow.submit(org.arjun, new SubmitCommand("CASUAL", LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-07"), "r", UUID.randomUUID())).request().getId();
        clock.advance(Duration.ofHours(49));
        assertThat(escalationJob.runOnce()).isEqualTo(1); // now both Meera (assigned) and Dev (skip-level) may act
        DecisionCommand approve = new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true);
        List<Object> results = race(List.of(
                () -> workflow.decide(org.meera, id, approve),
                () -> workflow.decide(org.dev, id, approve)));
        assertThat(results.stream().filter(r -> code(r).equals("OK")).count()).isGreaterThanOrEqualTo(1);
        assertThat(results).allMatch(r -> code(r).equals("OK") || code(r).equals("INVALID_TRANSITION"));
        assertThat(jdbc.queryForObject("select count(*) from approval_action where request_id=? and stage='MANAGER' and action='APPROVE'",
                Integer.class, id)).isEqualTo(1);
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
    }

    @Test
    void approvalAndEscalationRaceAlwaysStaysConsistent() throws Exception {
        for (int i = 0; i < 15; i++) {
            clock.reset();
            Long id = workflow.submit(org.arjun, new SubmitCommand("CASUAL", LocalDate.parse("2026-10-05"),
                    LocalDate.parse("2026-10-07"), "r", UUID.randomUUID())).request().getId();
            clock.advance(Duration.ofHours(49)); // manager deadline has passed
            List<Object> results = race(List.of(
                    () -> escalationJob.runOnce(),
                    () -> workflow.decide(org.meera, id, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true))));
            assertThat(code(results.get(1))).as("manager approval must never fail because of escalation").isEqualTo("OK");
            var r = requests.findById(id).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
            List<String> escalations = jdbc.queryForList(
                    "select coalesce(resolution,'OPEN') from escalation where request_id=?", String.class, id);
            // either the approval won (no escalation) or escalation happened first and was resolved by the approval
            assertThat(escalations).isIn(List.of(), List.of("APPROVED"));
            workflow.cancel(org.arjun, id, "cleanup"); // releases dates and balance for the next iteration
        }
        assertThat(dashboard.balanceIntegrity()).isEmpty();
    }

    @Test
    void cancelDuringHrApprovalLeavesConsistentBalance() throws Exception {
        for (int i = 0; i < 10; i++) {
            Long id = workflow.submit(org.bala, new SubmitCommand("ANNUAL", LocalDate.parse("2026-11-02"),
                    LocalDate.parse("2026-11-04"), "r", UUID.randomUUID())).request().getId();
            workflow.decide(org.meera, id, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true));
            List<Object> results = race(List.of(
                    () -> workflow.cancel(org.bala, id, null),
                    () -> workflow.decide(org.hema, id, new DecisionCommand(Stage.HR, Decision.APPROVE, null, true))));
            LeaveStatus s = requests.findById(id).orElseThrow().getStatus();
            // cancel always ends the story: either it cancelled a pending request (HR then gets 409),
            // or HR approved first and the owner cancelled the approved future leave.
            assertThat(s).isEqualTo(LeaveStatus.CANCELLED);
            assertThat(code(results.get(0))).isEqualTo("OK");
            assertThat(code(results.get(1))).isIn("OK", "INVALID_TRANSITION");
        }
        var b = balances.findByUserIdAndLeaveTypeCodeAndYear(org.bala.id(), "ANNUAL", 2026).orElseThrow();
        assertThat(b.getPendingDays()).isEqualByComparingTo("0");
        assertThat(b.getUsedDays()).isEqualByComparingTo("0");
        assertThat(dashboard.balanceIntegrity()).isEmpty();
    }

    @Test
    void differentTypesSameEmployeeOverlappingInParallelOnlyOneWins() throws Exception {
        LocalDate s = LocalDate.parse("2026-10-05");
        List<Object> results = race(List.of(
                () -> workflow.submit(org.chitra, new SubmitCommand("CASUAL", s, s.plusDays(1), "r", UUID.randomUUID())),
                () -> workflow.submit(org.chitra, new SubmitCommand("ANNUAL", s, s.plusDays(1), "r", UUID.randomUUID())),
                () -> workflow.submit(org.chitra, new SubmitCommand("SICK", s, s.plusDays(1), "r", UUID.randomUUID()))));
        assertThat(results.stream().filter(r -> code(r).equals("OK")).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> code(r).equals("OVERLAPPING_REQUEST")).count()).isEqualTo(2);
        assertThat(dashboard.balanceIntegrity()).isEmpty();
    }

    @SuppressWarnings("unused")
    private CurrentUser any() { return org.arjun; }
}
