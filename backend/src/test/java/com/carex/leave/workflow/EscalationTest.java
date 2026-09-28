package com.carex.leave.workflow;

import com.carex.leave.audit.AuditLogRepository;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.config.AppProperties;
import com.carex.leave.escalation.EscalationRepository;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.notification.NotificationRepository;
import com.carex.leave.support.IntegrationTest;
import com.carex.leave.workflow.LeaveWorkflowService.Decision;
import com.carex.leave.workflow.LeaveWorkflowService.DecisionCommand;
import com.carex.leave.workflow.LeaveWorkflowService.SubmitCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EscalationTest extends IntegrationTest {
    @Autowired LeaveWorkflowService workflow;
    @Autowired EscalationJob job;
    @Autowired LeaveRequestRepository requests;
    @Autowired EscalationRepository escalations;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository audit;
    @Autowired BusinessCalendar calendar;
    @Autowired AppProperties props;
    @Autowired PlatformTransactionManager txManager;

    static final DecisionCommand APPROVE_MGR = new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true);
    static final DecisionCommand APPROVE_HR = new DecisionCommand(Stage.HR, Decision.APPROVE, null, true);

    Long submit(com.carex.leave.auth.CurrentUser who) {
        return workflow.submit(who, new SubmitCommand("CASUAL", LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-07"), "r", UUID.randomUUID())).request().getId();
    }

    LeaveRequest req(Long id) { return requests.findById(id).orElseThrow(); }

    @Test
    void managerStageEscalatesToSkipLevelAfterTimeout() {
        Long id = submit(org.arjun);
        assertThat(job.runOnce()).isZero(); // not yet due
        clock.advance(Duration.ofHours(47));
        assertThat(job.runOnce()).isZero();
        clock.advance(Duration.ofHours(2));
        assertThat(job.runOnce()).isEqualTo(1);

        LeaveRequest r = req(id);
        assertThat(r.getStatus()).isEqualTo(LeaveStatus.MANAGER_ESCALATED);
        assertThat(r.getEscalationApproverId()).isEqualTo(org.dev.id());
        assertThat(r.getStageDeadlineAt()).isNull();
        var esc = escalations.findByRequestIdOrderByIdAsc(id);
        assertThat(esc).hasSize(1);
        assertThat(esc.get(0).getStage()).isEqualTo("MANAGER");
        assertThat(esc.get(0).getEscalatedToUserId()).isEqualTo(org.dev.id());
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(org.dev.id())).isPositive();
        var auditRow = audit.findByEntityTypeAndEntityIdOrderByIdAsc("LEAVE_REQUEST", id).stream()
                .filter(a -> a.getAction().equals("LEAVE_ESCALATED")).findFirst().orElseThrow();
        assertThat(auditRow.getActorRole()).isEqualTo("SYSTEM");
        assertThat(auditRow.getActorId()).isNull();

        // the skip-level manager can now decide at the manager stage; HR still has to approve
        workflow.decide(org.dev, id, APPROVE_MGR);
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(escalations.findByRequestIdOrderByIdAsc(id).get(0).getResolution()).isEqualTo("APPROVED");
    }

    @Test
    void originalManagerCanStillApproveAfterEscalation() {
        Long id = submit(org.arjun);
        clock.advance(Duration.ofHours(49));
        job.runOnce();
        workflow.decide(org.meera, id, APPROVE_MGR);
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
    }

    @Test
    void hrStageEscalationGoesToHrHeadAndNeverAutoApproves() {
        Long id = submit(org.arjun);
        workflow.decide(org.meera, id, APPROVE_MGR);
        clock.advance(Duration.ofHours(49));
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.HR_ESCALATED);
        assertThat(escalations.findByRequestIdOrderByIdAsc(id).get(0).getEscalatedToUserId()).isEqualTo(org.hema.id());
        clock.advance(Duration.ofDays(30));
        assertThat(job.runOnce()).isZero(); // escalates once per stage; never auto-approves
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.HR_ESCALATED);
        workflow.decide(org.harish, id, APPROVE_HR);
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void noSkipLevelFallsBackToHrPoolWithFourEyes() {
        Long id = submit(org.meera); // Meera → Dev; Dev manages Leadership himself → no skip-level
        clock.advance(Duration.ofHours(49));
        job.runOnce();
        LeaveRequest r = req(id);
        assertThat(r.getStatus()).isEqualTo(LeaveStatus.MANAGER_ESCALATED);
        assertThat(r.isEscalatedToHrPool()).isTrue();
        workflow.decide(org.harish, id, APPROVE_MGR); // HR proxy at manager stage
        assertThatThrownBy(() -> workflow.decide(org.harish, id, APPROVE_HR))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "FOUR_EYES_VIOLATION");
        workflow.decide(org.isha, id, APPROVE_HR);
        assertThat(req(id).getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void schedulerRunningConcurrentlyEscalatesExactlyOnce() throws Exception {
        Long id = submit(org.arjun);
        clock.advance(Duration.ofHours(49));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fs.add(pool.submit(() -> { gate.await(); return job.runOnce(); }));
        }
        gate.countDown();
        int total = 0;
        for (Future<Integer> f : fs) total += f.get();
        pool.shutdown();
        assertThat(total).isEqualTo(1);
        assertThat(escalations.countByRequestIdAndStage(id, "MANAGER")).isEqualTo(1);
        assertThat(job.runOnce()).isZero(); // and a later duplicate run does nothing
        assertThat(jdbc.queryForObject("select count(*) from approval_action where request_id=? and action='ESCALATE'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void escalationSurvivesRestartBecauseStateIsInTheDatabase() {
        Long id = submit(org.arjun);
        // "app is down" for 5 days: nothing ran. A brand-new job instance (as after a restart) recovers from DB state.
        clock.advance(Duration.ofDays(5));
        EscalationJob afterRestart = new EscalationJob(requests, workflow, calendar, props, txManager);
        assertThat(afterRestart.runOnce()).isEqualTo(1);
        var esc = escalations.findByRequestIdOrderByIdAsc(id).get(0);
        assertThat(Duration.between(esc.getDeadlineAt(), esc.getEscalatedAt())).isGreaterThan(Duration.ofDays(2));
    }

    @Test
    void cancelledRequestIsNeverEscalated() {
        Long id = submit(org.arjun);
        workflow.cancel(org.arjun, id, null);
        clock.advance(Duration.ofDays(3));
        assertThat(job.runOnce()).isZero();
        assertThat(escalations.findByRequestIdOrderByIdAsc(id)).isEmpty();
    }
}
