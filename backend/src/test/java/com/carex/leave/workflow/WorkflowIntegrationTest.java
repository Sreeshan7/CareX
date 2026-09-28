package com.carex.leave.workflow;

import com.carex.leave.audit.AuditLogRepository;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.leave.balance.LeaveBalance;
import com.carex.leave.leave.balance.LeaveBalanceRepository;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.support.IntegrationTest;
import com.carex.leave.workflow.LeaveWorkflowService.Decision;
import com.carex.leave.workflow.LeaveWorkflowService.DecisionCommand;
import com.carex.leave.workflow.LeaveWorkflowService.SubmitCommand;
import com.carex.leave.workflow.history.ApprovalActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowIntegrationTest extends IntegrationTest {
    @Autowired LeaveWorkflowService workflow;
    @Autowired LeaveRequestRepository requests;
    @Autowired LeaveBalanceRepository balances;
    @Autowired ApprovalActionRepository actions;
    @Autowired AuditLogRepository audit;
    @Autowired com.carex.leave.leave.balance.BalanceService balanceService;

    static final LocalDate MON = LocalDate.parse("2026-10-05");
    static final LocalDate WED = LocalDate.parse("2026-10-07");

    Long submit(CurrentUser who, String type, LocalDate s, LocalDate e) {
        return workflow.submit(who, new SubmitCommand(type, s, e, "reason", UUID.randomUUID())).request().getId();
    }

    DecisionCommand approve(Stage st) { return new DecisionCommand(st, Decision.APPROVE, null, true); }

    LeaveBalance bal(CurrentUser u, String type) {
        return balances.findByUserIdAndLeaveTypeCodeAndYear(u.id(), type, 2026).orElseThrow();
    }

    LeaveStatus status(Long id) { return requests.findById(id).orElseThrow().getStatus(); }

    @Test
    void happyPathManagerThenHrMovesPendingToUsed() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        assertThat(status(id)).isEqualTo(LeaveStatus.PENDING_MANAGER);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("3.0");
        assertThat(bal(org.arjun, "CASUAL").available()).isEqualByComparingTo("5.0");

        workflow.decide(org.meera, id, approve(Stage.MANAGER));
        assertThat(status(id)).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("3.0");

        workflow.decide(org.hema, id, approve(Stage.HR));
        assertThat(status(id)).isEqualTo(LeaveStatus.APPROVED);
        LeaveBalance b = bal(org.arjun, "CASUAL");
        assertThat(b.getPendingDays()).isEqualByComparingTo("0");
        assertThat(b.getUsedDays()).isEqualByComparingTo("3.0");
        assertThat(actions.findByRequestIdOrderByIdAsc(id)).extracting(a -> a.getAction())
                .containsExactly("SUBMIT", "APPROVE", "APPROVE");
        assertThat(audit.findByEntityTypeAndEntityIdOrderByIdAsc("LEAVE_REQUEST", id)).extracting(a -> a.getAction())
                .contains("LEAVE_SUBMITTED", "LEAVE_MANAGER_APPROVED", "LEAVE_HR_APPROVED");
    }

    @Test
    void hrCannotApproveAtManagerStage() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> workflow.decide(org.hema, id, approve(Stage.HR)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
        assertThat(status(id)).isEqualTo(LeaveStatus.PENDING_MANAGER);
    }

    @Test
    void managerCannotActAtHrStageAndCannotBypassHr() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        workflow.decide(org.meera, id, approve(Stage.MANAGER));
        // manager tries the HR stage → not an HR user
        assertThatThrownBy(() -> workflow.decide(org.meera, id, approve(Stage.HR)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_AN_APPROVER");
        // another manager-stage approval is a replay for Meera (idempotent) and a 409 for anyone else
        assertThat(workflow.decide(org.meera, id, approve(Stage.MANAGER)).replayed()).isTrue();
        assertThatThrownBy(() -> workflow.decide(org.hema, id, approve(Stage.MANAGER)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
        assertThat(status(id)).isEqualTo(LeaveStatus.PENDING_HR);
    }

    @Test
    void employeeCannotApproveOwnRequest() {
        Long id = submit(org.meera, "CASUAL", MON, WED); // Meera's own request goes to Dev
        assertThatThrownBy(() -> workflow.decide(org.meera, id, approve(Stage.MANAGER)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_AN_APPROVER");
        workflow.decide(org.dev, id, approve(Stage.MANAGER));
        assertThat(status(id)).isEqualTo(LeaveStatus.PENDING_HR);
    }

    @Test
    void hrEmployeeCannotApproveOwnRequestAtHrStage() {
        Long id = submit(org.harish, "CASUAL", MON, WED); // Harish's manager = Hema
        workflow.decide(org.hema, id, approve(Stage.MANAGER));
        assertThatThrownBy(() -> workflow.decide(org.harish, id, approve(Stage.HR)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_AN_APPROVER");
    }

    @Test
    void samePersonCannotApproveBothStages() {
        // Harish reports to Hema (HR head, role HR): Hema approves manager stage, then may NOT approve HR stage
        Long id = submit(org.harish, "CASUAL", MON, WED);
        workflow.decide(org.hema, id, approve(Stage.MANAGER));
        assertThatThrownBy(() -> workflow.decide(org.hema, id, approve(Stage.HR)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "FOUR_EYES_VIOLATION");
        workflow.decide(org.isha, id, approve(Stage.HR));
        assertThat(status(id)).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void topLevelUserIsRoutedToHrProxyAndStillNeedsTwoHumans() {
        Long id = submit(org.dev, "CASUAL", MON, WED);
        LeaveRequest r = requests.findById(id).orElseThrow();
        assertThat(r.isManagerRoutedToHr()).isTrue();
        assertThat(r.getManagerApproverId()).isEqualTo(org.hema.id());
        workflow.decide(org.harish, id, approve(Stage.MANAGER)); // HR proxy at manager stage
        assertThatThrownBy(() -> workflow.decide(org.harish, id, approve(Stage.HR)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "FOUR_EYES_VIOLATION");
        workflow.decide(org.isha, id, approve(Stage.HR));
        assertThat(status(id)).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void managerOfAnotherTeamCannotDecideAndCannotSee() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> workflow.decide(org.dev, id, approve(Stage.MANAGER)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void managerRejectRequiresCommentAndReleasesBalance() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> workflow.decide(org.meera, id, new DecisionCommand(Stage.MANAGER, Decision.REJECT, " ", false)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "COMMENT_REQUIRED");
        workflow.decide(org.meera, id, new DecisionCommand(Stage.MANAGER, Decision.REJECT, "Release week", false));
        assertThat(status(id)).isEqualTo(LeaveStatus.REJECTED);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("0");
        assertThat(bal(org.arjun, "CASUAL").available()).isEqualByComparingTo("8.0");
    }

    @Test
    void hrRejectReleasesBalance() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        workflow.decide(org.meera, id, approve(Stage.MANAGER));
        workflow.decide(org.hema, id, new DecisionCommand(Stage.HR, Decision.REJECT, "Policy", false));
        assertThat(status(id)).isEqualTo(LeaveStatus.REJECTED);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("0");
        assertThat(bal(org.arjun, "CASUAL").getUsedDays()).isEqualByComparingTo("0");
    }

    @Test
    void cancelPendingReleasesAndCancelApprovedRestoresUsed() {
        Long a = submit(org.arjun, "CASUAL", MON, WED);
        workflow.cancel(org.arjun, a, "plans changed");
        assertThat(status(a)).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("0");

        Long b = submit(org.arjun, "ANNUAL", MON, WED);
        workflow.decide(org.meera, b, approve(Stage.MANAGER));
        workflow.decide(org.hema, b, approve(Stage.HR));
        assertThat(bal(org.arjun, "ANNUAL").getUsedDays()).isEqualByComparingTo("3.0");
        workflow.cancel(org.arjun, b, null);
        assertThat(status(b)).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(bal(org.arjun, "ANNUAL").getUsedDays()).isEqualByComparingTo("0");
        // cancelling again is an idempotent replay; others cannot cancel
        assertThat(workflow.cancel(org.arjun, b, null).replayed()).isTrue();
    }

    @Test
    void cannotCancelApprovedLeaveThatStarted() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        workflow.decide(org.meera, id, approve(Stage.MANAGER));
        workflow.decide(org.hema, id, approve(Stage.HR));
        clock.set(java.time.Instant.parse("2026-10-05T05:00:00Z")); // start date reached
        assertThatThrownBy(() -> workflow.cancel(org.arjun, id, null))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "LEAVE_ALREADY_STARTED");
    }

    @Test
    void onlyOwnerCanCancel() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> workflow.cancel(org.meera, id, null))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_OWNER");
    }

    @Test
    void cannotApproveAfterCancel() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        workflow.cancel(org.arjun, id, null);
        assertThatThrownBy(() -> workflow.decide(org.meera, id, approve(Stage.MANAGER)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
    }

    @Test
    void insufficientBalanceIsRejected() {
        // Farhan (joined 2026-07-20): casual 8 × 5/12 = 3.33 → 3.5 days → 4 days must fail
        assertThatThrownBy(() -> submit(org.farhan, "CASUAL", MON, LocalDate.parse("2026-10-08")))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");
        balanceService.balancesFor(org.farhan.id(), 2026); // rows were rolled back with the failed submit
        assertThat(bal(org.farhan, "CASUAL").getEntitledDays()).isEqualByComparingTo("3.5");
        assertThat(bal(org.farhan, "CASUAL").getPendingDays()).isEqualByComparingTo("0");
        assertThat(bal(org.farhan, "ANNUAL").getEntitledDays()).isEqualByComparingTo("7.5");
    }

    @Test
    void overlappingRequestRejectedEvenAcrossLeaveTypes() {
        submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> submit(org.arjun, "ANNUAL", WED, WED.plusDays(1)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "OVERLAPPING_REQUEST");
    }

    @Test
    void validationRules() {
        assertThatThrownBy(() -> submit(org.arjun, "CASUAL", LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-25")))
                .hasFieldOrPropertyWithValue("code", "BACKDATED_NOT_ALLOWED");
        assertThatThrownBy(() -> submit(org.arjun, "CASUAL", LocalDate.parse("2026-12-31"), LocalDate.parse("2027-01-01")))
                .hasFieldOrPropertyWithValue("code", "SPANS_YEARS");
        assertThatThrownBy(() -> submit(org.arjun, "CASUAL", LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11")))
                .hasFieldOrPropertyWithValue("code", "NO_WORKING_DAYS");
        assertThatThrownBy(() -> submit(org.arjun, "NOPE", MON, WED))
                .hasFieldOrPropertyWithValue("code", "INVALID_LEAVE_TYPE");
        assertThatThrownBy(() -> submit(org.arjun, "CASUAL", WED, MON))
                .hasFieldOrPropertyWithValue("code", "INVALID_DATE_RANGE");
        // SICK can be backdated up to 7 days
        Long sick = submit(org.arjun, "SICK", LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-25"));
        assertThat(status(sick)).isEqualTo(LeaveStatus.PENDING_MANAGER);
    }

    @Test
    void holidayIsExcludedFromWorkingDays() {
        Long id = submit(org.arjun, "ANNUAL", LocalDate.parse("2026-09-29"), LocalDate.parse("2026-10-05"));
        // Tue..Mon = 5 weekdays minus Gandhi Jayanti (Fri 2 Oct) = 4
        assertThat(requests.findById(id).orElseThrow().getWorkingDays()).isEqualByComparingTo(new BigDecimal("4.0"));
    }

    @Test
    void duplicateSubmitWithSameClientIdCreatesOneRequest() {
        UUID key = UUID.randomUUID();
        var cmd = new SubmitCommand("CASUAL", MON, WED, "x", key);
        var first = workflow.submit(org.arjun, cmd);
        var second = workflow.submit(org.arjun, cmd);
        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.request().getId()).isEqualTo(first.request().getId());
        assertThat(requests.count()).isEqualTo(1);
        assertThat(bal(org.arjun, "CASUAL").getPendingDays()).isEqualByComparingTo("3.0");
    }

    @Test
    void conflictIsFlaggedNotRejectedAndApprovalNeedsAcknowledgement() {
        Long arjun = submit(org.arjun, "CASUAL", MON, WED);
        Long bala = submit(org.bala, "ANNUAL", LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-08"));
        assertThat(status(bala)).isEqualTo(LeaveStatus.PENDING_MANAGER); // flagged, never auto-rejected
        assertThat(jdbc.queryForObject("select flagged from conflict_flag where request_id = ?", Boolean.class, bala)).isTrue();
        assertThat(jdbc.queryForObject("select flagged from conflict_flag where request_id = ?", Boolean.class, arjun)).isFalse();

        assertThatThrownBy(() -> workflow.decide(org.meera, bala, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, false)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "CONFLICT_ACK_REQUIRED");
        assertThat(status(bala)).isEqualTo(LeaveStatus.PENDING_MANAGER);
        workflow.decide(org.meera, bala, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true));
        assertThat(status(bala)).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(jdbc.queryForObject("select acknowledged_by_manager from conflict_flag where request_id = ?", Long.class, bala))
                .isEqualTo(org.meera.id());
        assertThat(audit.countByAction("CONFLICT_FLAGGED")).isEqualTo(1);
        assertThat(audit.countByAction("CONFLICT_ACKNOWLEDGED")).isEqualTo(1);
        // rejecting a flagged request needs no acknowledgement
        workflow.decide(org.meera, arjun, new DecisionCommand(Stage.MANAGER, Decision.REJECT, "Too many people out", false));
    }

    @Test
    void everyConsequentialTransitionIsAudited() {
        Long id = submit(org.arjun, "CASUAL", MON, WED);
        workflow.decide(org.meera, id, approve(Stage.MANAGER));
        workflow.decide(org.hema, id, approve(Stage.HR));
        workflow.cancel(org.arjun, id, null);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByIdAsc("LEAVE_REQUEST", id)).extracting(a -> a.getAction())
                .containsExactly("LEAVE_SUBMITTED", "LEAVE_MANAGER_APPROVED", "LEAVE_HR_APPROVED", "LEAVE_CANCELLED");
        assertThat(audit.countByAction("BALANCE_CONSUMED")).isEqualTo(1);
        assertThat(audit.countByAction("BALANCE_RESTORED")).isEqualTo(1);
        // a denied attempt is audited too (separate transaction)
        Long other = submit(org.bala, "CASUAL", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-02"));
        try { workflow.decide(org.hema, other, approve(Stage.HR)); } catch (ApiException ignored) { }
        assertThat(audit.countByAction("TRANSITION_DENIED")).isEqualTo(1);
    }

    @Test
    void auditLogIsAppendOnly() {
        submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> jdbc.update("update audit_log set summary = 'tampered'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("append-only");
    }

    @Test
    void balanceCheckConstraintIsTheBackstop() {
        submit(org.arjun, "CASUAL", MON, WED);
        assertThatThrownBy(() -> jdbc.update("update leave_balance set pending_days = 100 where user_id = ?", org.arjun.id()))
                .hasMessageContaining("ck_balance_not_overdrawn");
    }
}
