package com.carex.leave.workflow.policy;

import com.carex.leave.common.error.Errors;
import com.carex.leave.leave.request.LeaveStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static com.carex.leave.leave.request.LeaveStatus.*;

/**
 * The approval state machine (implementation.md §7.2). Pure: no Spring, no DB. Authorization is NOT here —
 * see {@link AccessPolicy}. There is deliberately no event that moves a manager-stage state to APPROVED.
 */
public final class LeaveStateMachine {
    private static final Map<LeaveEvent, Map<LeaveStatus, LeaveStatus>> TABLE = new EnumMap<>(LeaveEvent.class);

    static {
        put(LeaveEvent.MANAGER_APPROVE, PENDING_MANAGER, PENDING_HR);
        put(LeaveEvent.MANAGER_APPROVE, MANAGER_ESCALATED, PENDING_HR);
        put(LeaveEvent.MANAGER_REJECT, PENDING_MANAGER, REJECTED);
        put(LeaveEvent.MANAGER_REJECT, MANAGER_ESCALATED, REJECTED);
        put(LeaveEvent.HR_APPROVE, PENDING_HR, APPROVED);
        put(LeaveEvent.HR_APPROVE, HR_ESCALATED, APPROVED);
        put(LeaveEvent.HR_REJECT, PENDING_HR, REJECTED);
        put(LeaveEvent.HR_REJECT, HR_ESCALATED, REJECTED);
        put(LeaveEvent.ESCALATE, PENDING_MANAGER, MANAGER_ESCALATED);
        put(LeaveEvent.ESCALATE, PENDING_HR, HR_ESCALATED);
        put(LeaveEvent.CANCEL, PENDING_MANAGER, CANCELLED);
        put(LeaveEvent.CANCEL, MANAGER_ESCALATED, CANCELLED);
        put(LeaveEvent.CANCEL, PENDING_HR, CANCELLED);
        put(LeaveEvent.CANCEL, HR_ESCALATED, CANCELLED);
        put(LeaveEvent.CANCEL, APPROVED, CANCELLED);
    }

    private LeaveStateMachine() {}

    private static void put(LeaveEvent e, LeaveStatus from, LeaveStatus to) {
        TABLE.computeIfAbsent(e, k -> new EnumMap<>(LeaveStatus.class)).put(from, to);
    }

    /** Initial state for SUBMIT. */
    public static LeaveStatus initial() {
        return PENDING_MANAGER;
    }

    public static Optional<LeaveStatus> tryNext(LeaveStatus current, LeaveEvent event) {
        if (event == LeaveEvent.SUBMIT) {
            return Optional.empty(); // SUBMIT only creates; it never applies to an existing request
        }
        return Optional.ofNullable(TABLE.getOrDefault(event, Map.of()).get(current));
    }

    public static boolean allows(LeaveStatus current, LeaveEvent event) {
        return tryNext(current, event).isPresent();
    }

    public static LeaveStatus next(LeaveStatus current, LeaveEvent event) {
        return tryNext(current, event).orElseThrow(() -> Errors.invalidTransition(current.name(),
                "Cannot " + event.name() + " a request in status " + current.name()));
    }

    public static Map<LeaveEvent, Map<LeaveStatus, LeaveStatus>> table() {
        return Collections.unmodifiableMap(TABLE);
    }
}
