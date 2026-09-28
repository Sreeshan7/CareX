package com.carex.leave.workflow;

import com.carex.leave.audit.AuditAction;
import com.carex.leave.audit.AuditService;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.error.Errors;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.common.web.ClientChannel;
import com.carex.leave.config.AppProperties;
import com.carex.leave.conflict.ConflictFlag;
import com.carex.leave.conflict.ConflictService;
import com.carex.leave.escalation.Escalation;
import com.carex.leave.escalation.EscalationRepository;
import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.balance.LeaveBalance;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveRequestValidator;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.leave.type.LeaveType;
import com.carex.leave.leave.type.LeaveTypeRepository;
import com.carex.leave.notification.NotificationService;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.workflow.history.ApprovalAction;
import com.carex.leave.workflow.history.ApprovalActionRepository;
import com.carex.leave.workflow.policy.AccessPolicy;
import com.carex.leave.workflow.policy.ActorCapacity;
import com.carex.leave.workflow.policy.LeaveEvent;
import com.carex.leave.workflow.policy.LeaveStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ONLY component that changes leave_request.status or balance numbers (implementation.md §8).
 * Every operation is one READ COMMITTED transaction with the fixed lock order:
 * (1) team advisory lock [submit only] → (2) leave_request row → (3) leave_balance row → (4) inserts.
 * No external (Sarvam) calls happen inside these transactions.
 */
@Service
public class LeaveWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(LeaveWorkflowService.class);
    private static final String ENTITY = "LEAVE_REQUEST";

    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final ApprovalActionRepository actions;
    private final EscalationRepository escalations;
    private final BalanceService balances;
    private final ConflictService conflicts;
    private final AccessPolicy policy;
    private final OrgService org;
    private final LeaveRequestValidator validator;
    private final AuditService audit;
    private final NotificationService notifications;
    private final BusinessCalendar calendar;
    private final AppProperties props;

    public LeaveWorkflowService(LeaveRequestRepository requests, LeaveTypeRepository types,
                                ApprovalActionRepository actions, EscalationRepository escalations,
                                BalanceService balances, ConflictService conflicts, AccessPolicy policy,
                                OrgService org, LeaveRequestValidator validator, AuditService audit,
                                NotificationService notifications, BusinessCalendar calendar, AppProperties props) {
        this.requests = requests;
        this.types = types;
        this.actions = actions;
        this.escalations = escalations;
        this.balances = balances;
        this.conflicts = conflicts;
        this.policy = policy;
        this.org = org;
        this.validator = validator;
        this.audit = audit;
        this.notifications = notifications;
        this.calendar = calendar;
        this.props = props;
    }

    public record SubmitCommand(String leaveTypeCode, LocalDate startDate, LocalDate endDate, String reason,
                                UUID clientRequestId) {}

    public enum Decision { APPROVE, REJECT }

    public record DecisionCommand(Stage stage, Decision decision, String comment, boolean acknowledgeConflict) {}

    public record Result(LeaveRequest request, boolean replayed) {}

    // =====================================================================================
    // T1 SUBMIT
    // =====================================================================================
    @Transactional
    public Result submit(CurrentUser me, SubmitCommand cmd) {
        ClientChannel channel = ClientChannel.current().forRequest();
        Optional<LeaveRequest> existing = requests.findByEmployeeIdAndClientRequestId(me.id(), cmd.clientRequestId());
        if (existing.isPresent()) {
            return new Result(existing.get(), true);
        }
        AppUser user = org.user(me.id());
        LeaveType type = cmd.leaveTypeCode() == null ? null : types.findById(cmd.leaveTypeCode()).orElse(null);
        WorkingDayCalculator.Breakdown bd = validator.validateOrThrow(user, type, cmd.startDate(), cmd.endDate());
        OrgService.ManagerRouting routing = org.managerApproverFor(user);

        // (1) serialize submissions per team: conflict evaluation must see concurrent teammates
        requests.lockTeam(user.getTeamId());
        // idempotency re-check after the lock (concurrent duplicate submit with the same key)
        existing = requests.findByEmployeeIdAndClientRequestId(me.id(), cmd.clientRequestId());
        if (existing.isPresent()) {
            return new Result(existing.get(), true);
        }
        if (!requests.findOverlappingForEmployee(me.id(), LeaveStatus.ACTIVE, cmd.startDate(), cmd.endDate()).isEmpty()) {
            throw Errors.conflict("OVERLAPPING_REQUEST", "You already have leave on some of these dates");
        }

        // (3) balance row lock + reservation (DB CHECK is the backstop)
        LeaveBalance balance = balances.lockOrCreate(me.id(), type.getCode(), cmd.startDate().getYear());
        BigDecimal days = BigDecimal.valueOf(bd.count()).setScale(1);
        Map<String, Object> balanceBefore = balanceState(balance);
        balances.reserve(balance, days);

        Instant now = calendar.now();
        LeaveRequest r = new LeaveRequest(me.id(), type.getCode(), cmd.startDate(), cmd.endDate(), days,
                LeaveRequestValidator.sanitizeReason(cmd.reason()), user.getTeamId(), routing.approverId(),
                routing.routedToHr(), cmd.clientRequestId(), channel.name(), now,
                now.plus(props.escalation().managerTimeout()));
        requests.saveAndFlush(r); // exclusion constraint ex_request_no_overlap is the backstop for overlaps

        ConflictFlag flag = conflicts.evaluateAndPersist(r, "SUBMIT");
        recordAction(r, Stage.NONE, LeaveEvent.SUBMIT, me.id(), ActorCapacity.OWNER, null, r.getStatus(), null, false, now);

        audit.record(me, AuditAction.LEAVE_SUBMITTED, ENTITY, r.getId(),
                "Submitted " + days + " day(s) " + type.getCode() + " " + r.getStartDate() + ".." + r.getEndDate(),
                Map.of("balance", balanceBefore), withBalance(requestState(r), balance));
        if (flag.isFlagged()) {
            audit.record(me, AuditAction.CONFLICT_FLAGGED, ENTITY, r.getId(),
                    "Team absence above threshold on " + flag.getPeakDate() + " (" + flag.getPeakAbsentCount() + "/" + flag.getTeamSize() + ")",
                    null, conflictState(flag));
        }

        String who = user.getFullName();
        String title = (flag.isFlagged() ? "⚠ Flagged: " : "") + "New leave request #" + r.getId();
        String body = who + " requested " + days + " day(s) " + type.getDisplayName() + " (" + r.getStartDate() + " → " + r.getEndDate() + ")"
                + (flag.isFlagged() ? ". High team absence on " + flag.getPeakDate() + "." : ".");
        notifications.notify(managerStageApprovers(r), "REQUEST_SUBMITTED", r.getId(), title, body);
        log.info("request={} submitted by={} days={} flagged={}", r.getId(), me.id(), days, flag.isFlagged());
        return new Result(r, false);
    }

    // =====================================================================================
    // T2–T5 DECISIONS
    // =====================================================================================
    @Transactional
    public Result decide(CurrentUser me, Long requestId, DecisionCommand cmd) {
        if (cmd.stage() == null || cmd.stage() == Stage.NONE || cmd.decision() == null) {
            throw Errors.badRequest("VALIDATION_FAILED", "stage (MANAGER|HR) and decision (APPROVE|REJECT) are required");
        }
        // (2) request row lock
        LeaveRequest r = requests.findByIdForUpdate(requestId).orElseThrow(() -> Errors.notFound("Leave request not found"));
        policy.assertVisible(me, r);
        boolean approve = cmd.decision() == Decision.APPROVE;
        LeaveEvent event = LeaveEvent.decision(cmd.stage(), approve);
        LeaveStatus from = r.getStatus();

        if (!LeaveStateMachine.allows(from, event)) {
            if (actions.existsByRequestIdAndStageAndActionAndActorId(r.getId(), cmd.stage().name(), event.actionName(), me.id())) {
                return new Result(r, true); // idempotent replay of my own decision
            }
            denied(me, r, AuditAction.TRANSITION_DENIED, event + " not allowed from " + from);
            throw Errors.invalidTransition(from.name(), "Cannot " + event.name() + " a request in status " + from.name());
        }
        ActorCapacity capacity;
        try {
            capacity = policy.capacityFor(me, r, event);
        } catch (ApiException e) {
            denied(me, r, AuditAction.ACCESS_DENIED, event + " denied: " + e.getCode());
            throw e;
        }
        String comment = cmd.comment() == null ? null : cmd.comment().trim();
        if (!approve && (comment == null || comment.length() < 3)) {
            throw Errors.businessRule("COMMENT_REQUIRED", "A reason (at least 3 characters) is required to reject");
        }

        ConflictFlag flag = null;
        if (approve) {
            flag = conflicts.evaluateAndPersist(r, cmd.stage() == Stage.MANAGER ? "MANAGER_DECISION" : "HR_DECISION");
            if (flag.isFlagged() && !cmd.acknowledgeConflict()) {
                throw Errors.businessRule("CONFLICT_ACK_REQUIRED",
                        "This request is flagged for high team absence — acknowledge the conflict to approve")
                        .with("peakDate", flag.getPeakDate()).with("peakAbsent", flag.getPeakAbsentCount())
                        .with("teamSize", flag.getTeamSize());
            }
        }

        LeaveStatus next = LeaveStateMachine.next(from, event);
        Instant now = calendar.now();
        Map<String, Object> before = requestState(r);
        LeaveBalance balance = null;
        switch (event) {
            case MANAGER_APPROVE -> {
                r.setManagerDecidedBy(me.id());
                r.transitionTo(next, now);
                r.setStageDeadlineAt(now.plus(props.escalation().hrTimeout()));
                resolveEscalation(r.getId(), Stage.MANAGER, "APPROVED", now);
            }
            case MANAGER_REJECT -> {
                balance = balances.lockOrCreate(r.getEmployeeId(), r.getLeaveTypeCode(), r.getStartDate().getYear());
                balances.releasePending(balance, r.getWorkingDays());
                r.setManagerDecidedBy(me.id());
                finish(r, next, now);
                resolveEscalation(r.getId(), Stage.MANAGER, "REJECTED", now);
            }
            case HR_APPROVE -> {
                balance = balances.lockOrCreate(r.getEmployeeId(), r.getLeaveTypeCode(), r.getStartDate().getYear());
                balances.commitPending(balance, r.getWorkingDays());
                r.setHrDecidedBy(me.id());
                finish(r, next, now);
                resolveEscalation(r.getId(), Stage.HR, "APPROVED", now);
            }
            case HR_REJECT -> {
                balance = balances.lockOrCreate(r.getEmployeeId(), r.getLeaveTypeCode(), r.getStartDate().getYear());
                balances.releasePending(balance, r.getWorkingDays());
                r.setHrDecidedBy(me.id());
                finish(r, next, now);
                resolveEscalation(r.getId(), Stage.HR, "REJECTED", now);
            }
            default -> throw new IllegalStateException("Unexpected decision event " + event);
        }
        requests.saveAndFlush(r);

        boolean acknowledged = approve && flag != null && flag.isFlagged();
        if (acknowledged) {
            conflicts.acknowledge(flag, me.id(), cmd.stage());
            audit.record(me, AuditAction.CONFLICT_ACKNOWLEDGED, ENTITY, r.getId(),
                    "Conflict acknowledged at " + cmd.stage() + " stage", null, conflictState(flag));
        }
        // uq_action_one_decision_per_stage is the final DB guarantee of one decision per stage
        recordAction(r, cmd.stage(), event, me.id(), capacity, from, next, comment, acknowledged, now);

        AuditAction auditAction = switch (event) {
            case MANAGER_APPROVE -> AuditAction.LEAVE_MANAGER_APPROVED;
            case MANAGER_REJECT -> AuditAction.LEAVE_MANAGER_REJECTED;
            case HR_APPROVE -> AuditAction.LEAVE_HR_APPROVED;
            default -> AuditAction.LEAVE_HR_REJECTED;
        };
        Map<String, Object> after = requestState(r);
        after.put("capacity", capacity.name());
        audit.record(me, auditAction, ENTITY, r.getId(), event + " by " + capacity + " (" + from + " → " + next + ")",
                before, balance == null ? after : withBalance(after, balance));
        if (event == LeaveEvent.HR_APPROVE) {
            audit.record(me, AuditAction.BALANCE_CONSUMED, "LEAVE_BALANCE", balance.getId(),
                    r.getWorkingDays() + " day(s) moved from pending to used", null, balanceState(balance));
        }
        notifyDecision(me, r, event, comment);
        log.info("request={} {} by={} capacity={} {}→{}", r.getId(), event, me.id(), capacity, from, next);
        return new Result(r, false);
    }

    // =====================================================================================
    // T8–T9 CANCEL
    // =====================================================================================
    @Transactional
    public Result cancel(CurrentUser me, Long requestId, String reason) {
        LeaveRequest r = requests.findByIdForUpdate(requestId).orElseThrow(() -> Errors.notFound("Leave request not found"));
        policy.assertVisible(me, r);
        LeaveStatus from = r.getStatus();
        if (!LeaveStateMachine.allows(from, LeaveEvent.CANCEL)) {
            if (from == LeaveStatus.CANCELLED && policy.isOwner(me, r)) {
                return new Result(r, true);
            }
            denied(me, r, AuditAction.TRANSITION_DENIED, "CANCEL not allowed from " + from);
            throw Errors.invalidTransition(from.name(), "Cannot cancel a request in status " + from.name());
        }
        ActorCapacity capacity;
        try {
            capacity = policy.capacityFor(me, r, LeaveEvent.CANCEL);
        } catch (ApiException e) {
            denied(me, r, AuditAction.ACCESS_DENIED, "CANCEL denied: " + e.getCode());
            throw e;
        }
        if (from == LeaveStatus.APPROVED && !r.getStartDate().isAfter(calendar.today())) {
            throw Errors.businessRule("LEAVE_ALREADY_STARTED", "Approved leave can only be cancelled before it starts");
        }
        Map<String, Object> before = requestState(r);
        LeaveBalance balance = balances.lockOrCreate(r.getEmployeeId(), r.getLeaveTypeCode(), r.getStartDate().getYear());
        if (from.isPending()) {
            balances.releasePending(balance, r.getWorkingDays());
        } else {
            balances.restoreUsed(balance, r.getWorkingDays());
        }
        Instant now = calendar.now();
        LeaveStatus next = LeaveStateMachine.next(from, LeaveEvent.CANCEL);
        r.transitionTo(next, now);
        r.setStageDeadlineAt(null);
        r.setCancelledAt(now);
        requests.saveAndFlush(r);
        resolveEscalation(r.getId(), Stage.MANAGER, "CANCELLED", now);
        resolveEscalation(r.getId(), Stage.HR, "CANCELLED", now);

        String comment = LeaveRequestValidator.sanitizeReason(reason);
        recordAction(r, from.stage(), LeaveEvent.CANCEL, me.id(), capacity, from, next, comment, false, now);
        audit.record(me, AuditAction.LEAVE_CANCELLED, ENTITY, r.getId(), "Cancelled (" + from + " → CANCELLED)",
                before, withBalance(requestState(r), balance));
        if (from == LeaveStatus.APPROVED) {
            audit.record(me, AuditAction.BALANCE_RESTORED, "LEAVE_BALANCE", balance.getId(),
                    r.getWorkingDays() + " used day(s) restored", null, balanceState(balance));
        }
        List<Long> recipients = new ArrayList<>();
        if (from.stage() == Stage.MANAGER) {
            recipients.addAll(managerStageApprovers(r));
        } else if (from.stage() == Stage.HR) {
            recipients.addAll(hrIdsExcept(r.getEmployeeId()));
        } else {
            recipients.add(r.getManagerDecidedBy());
            recipients.add(r.getHrDecidedBy());
        }
        recipients.remove(me.id());
        notifications.notify(recipients, "REQUEST_CANCELLED", r.getId(), "Leave request #" + r.getId() + " cancelled",
                org.user(r.getEmployeeId()).getFullName() + " cancelled their leave " + r.getStartDate() + " → " + r.getEndDate() + ".");
        return new Result(r, false);
    }

    // =====================================================================================
    // T6–T7 ESCALATE (SYSTEM). Caller must already hold the row lock (EscalationJob claim).
    // =====================================================================================
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean escalateLocked(Long requestId, Instant now) {
        LeaveRequest r = requests.findByIdForUpdate(requestId).orElse(null);
        if (r == null) return false;
        LeaveStatus from = r.getStatus();
        Instant deadline = r.getStageDeadlineAt();
        // defensive re-check under the lock: a concurrent approval/cancel may have committed first
        if ((from != LeaveStatus.PENDING_MANAGER && from != LeaveStatus.PENDING_HR) || deadline == null || deadline.isAfter(now)) {
            return false;
        }
        LeaveStatus next = LeaveStateMachine.next(from, LeaveEvent.ESCALATE);
        Stage stage = from.stage();
        Long targetUser;
        String targetRole;
        List<Long> recipients = new ArrayList<>();
        if (stage == Stage.MANAGER) {
            Optional<Long> skip = r.isManagerRoutedToHr() ? Optional.empty()
                    : org.skipLevelOf(r.getManagerApproverId(), r.getEmployeeId());
            if (skip.isPresent()) {
                targetUser = skip.get();
                targetRole = "MANAGER";
                r.setEscalationApproverId(targetUser);
                recipients.add(targetUser);
            } else {
                targetUser = org.hrHead().map(AppUser::getId).orElse(null);
                targetRole = "HR";
                r.setEscalatedToHrPool(true);
                recipients.addAll(hrIdsExcept(r.getEmployeeId()));
            }
            recipients.add(r.getManagerApproverId());
        } else {
            targetUser = org.hrHead().map(AppUser::getId).orElse(null);
            targetRole = "HR";
            recipients.addAll(hrIdsExcept(r.getEmployeeId()));
        }
        // uq_escalation_request_stage: a second escalation of the same stage fails here and rolls back
        escalations.saveAndFlush(new Escalation(r.getId(), stage.name(), deadline, now, targetUser, targetRole));
        Map<String, Object> before = requestState(r);
        r.transitionTo(next, now);
        r.setStageDeadlineAt(null);
        requests.saveAndFlush(r);
        recordAction(r, stage, LeaveEvent.ESCALATE, null, ActorCapacity.SYSTEM, from, next, null, false, now);
        Map<String, Object> after = requestState(r);
        after.put("escalatedToUserId", targetUser);
        after.put("escalatedToRole", targetRole);
        after.put("lateBySeconds", Math.max(0, now.getEpochSecond() - deadline.getEpochSecond()));
        audit.recordSystem(AuditAction.LEAVE_ESCALATED, ENTITY, r.getId(),
                stage + " stage timed out; escalated to " + targetRole + (targetUser == null ? "" : " #" + targetUser),
                before, after);
        String employee = org.user(r.getEmployeeId()).getFullName();
        recipients.add(r.getEmployeeId());
        notifications.notify(recipients, "REQUEST_ESCALATED", r.getId(), "⏰ Escalated: leave request #" + r.getId(),
                employee + "'s request waited too long at the " + stage.name().toLowerCase() + " stage and was escalated.");
        log.info("request={} escalated stage={} to={}/{}", r.getId(), stage, targetRole, targetUser);
        return true;
    }

    // =====================================================================================
    // helpers
    // =====================================================================================
    private void finish(LeaveRequest r, LeaveStatus next, Instant now) {
        r.transitionTo(next, now);
        r.setStageDeadlineAt(null);
        r.setDecidedAt(now);
    }

    private void resolveEscalation(Long requestId, Stage stage, String resolution, Instant now) {
        escalations.findByRequestIdAndStageAndResolvedAtIsNull(requestId, stage.name()).ifPresent(e -> {
            e.resolve(now, resolution);
            escalations.save(e);
        });
    }

    private void recordAction(LeaveRequest r, Stage stage, LeaveEvent event, Long actorId, ActorCapacity capacity,
                              LeaveStatus from, LeaveStatus to, String comment, boolean ack, Instant now) {
        String channel = actorId == null ? "SYSTEM" : ClientChannel.current().forRequest().name();
        actions.saveAndFlush(new ApprovalAction(r.getId(), stage.name(), event.actionName(), actorId, capacity.name(),
                from == null ? null : from.name(), to.name(), comment, ack, channel, now));
    }

    private void denied(CurrentUser me, LeaveRequest r, AuditAction action, String summary) {
        try {
            audit.recordIsolated(me, null, action, ENTITY, r.getId(), summary, null);
        } catch (RuntimeException e) {
            log.warn("could not audit denial: {}", e.getMessage());
        }
    }

    /** Who acts at the manager stage right now (for notifications). */
    private List<Long> managerStageApprovers(LeaveRequest r) {
        List<Long> out = new ArrayList<>();
        if (r.isManagerRoutedToHr() || r.isEscalatedToHrPool()) {
            out.addAll(hrIdsExcept(r.getEmployeeId()));
        } else {
            out.add(r.getManagerApproverId());
        }
        if (r.getEscalationApproverId() != null) out.add(r.getEscalationApproverId());
        return out;
    }

    private List<Long> hrIdsExcept(Long userId) {
        return org.hrUsers().stream().map(AppUser::getId).filter(id -> !id.equals(userId)).toList();
    }

    private void notifyDecision(CurrentUser me, LeaveRequest r, LeaveEvent event, String comment) {
        String employee = org.user(r.getEmployeeId()).getFullName();
        String range = r.getStartDate() + " → " + r.getEndDate();
        switch (event) {
            case MANAGER_APPROVE -> {
                notifications.notify(List.of(r.getEmployeeId()), "MANAGER_APPROVED", r.getId(),
                        "Manager approved #" + r.getId(), me.fullName() + " approved your leave " + range + ". Waiting for HR.");
                List<Long> hr = new ArrayList<>(hrIdsExcept(r.getEmployeeId()));
                hr.remove(me.id());
                notifications.notify(hr, "HR_ACTION_NEEDED", r.getId(), "HR approval needed #" + r.getId(),
                        employee + "'s leave " + range + " was approved by the manager and needs HR approval.");
            }
            case MANAGER_REJECT -> notifications.notify(List.of(r.getEmployeeId()), "REJECTED", r.getId(),
                    "Leave request #" + r.getId() + " rejected", me.fullName() + " rejected your leave " + range + ": " + comment);
            case HR_APPROVE -> notifications.notify(List.of(r.getEmployeeId(), r.getManagerDecidedBy()), "APPROVED", r.getId(),
                    "Leave approved #" + r.getId(), employee + "'s leave " + range + " is fully approved.");
            case HR_REJECT -> notifications.notify(List.of(r.getEmployeeId(), r.getManagerDecidedBy()), "REJECTED", r.getId(),
                    "Leave request #" + r.getId() + " rejected by HR", "HR rejected the leave " + range + ": " + comment);
            default -> { }
        }
    }

    private static Map<String, Object> requestState(LeaveRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus().name());
        m.put("leaveType", r.getLeaveTypeCode());
        m.put("startDate", r.getStartDate().toString());
        m.put("endDate", r.getEndDate().toString());
        m.put("workingDays", r.getWorkingDays());
        m.put("stageDeadlineAt", r.getStageDeadlineAt() == null ? null : r.getStageDeadlineAt().toString());
        return m;
    }

    private static Map<String, Object> balanceState(LeaveBalance b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", b.getLeaveTypeCode());
        m.put("year", b.getYear());
        m.put("entitled", b.getEntitledDays());
        m.put("used", b.getUsedDays());
        m.put("pending", b.getPendingDays());
        m.put("available", b.available());
        return m;
    }

    private static Map<String, Object> withBalance(Map<String, Object> state, LeaveBalance b) {
        state.put("balance", balanceState(b));
        return state;
    }

    private static Map<String, Object> conflictState(ConflictFlag f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flagged", f.isFlagged());
        m.put("peakDate", f.getPeakDate() == null ? null : f.getPeakDate().toString());
        m.put("peakAbsent", f.getPeakAbsentCount());
        m.put("teamSize", f.getTeamSize());
        m.put("thresholdPct", f.getThresholdPct());
        return m;
    }
}
