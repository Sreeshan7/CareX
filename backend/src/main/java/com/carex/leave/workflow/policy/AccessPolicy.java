package com.carex.leave.workflow.policy;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.error.Errors;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.org.OrgService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Object-level authorization (implementation.md §14.2). Called from services, so REST and the assistant share it.
 * Invisible resources are reported as 404 (no existence oracle).
 */
@Component
public class AccessPolicy {
    private final OrgService org;

    public AccessPolicy(OrgService org) {
        this.org = org;
    }

    public boolean canView(CurrentUser me, LeaveRequest r) {
        if (r.getEmployeeId().equals(me.id())) return true;
        if (me.isHr()) return true;
        if (me.id().equals(r.getManagerApproverId())) return true;
        if (me.id().equals(r.getEscalationApproverId())) return true;
        return me.isManager() && org.teamIdsManagedBy(me.id()).contains(r.getTeamId());
    }

    public void assertVisible(CurrentUser me, LeaveRequest r) {
        if (!canView(me, r)) {
            throw Errors.notFound("Leave request not found");
        }
    }

    public boolean isOwner(CurrentUser me, LeaveRequest r) {
        return r.getEmployeeId().equals(me.id());
    }

    /** Returns the capacity in which {@code me} may perform {@code event}, or throws 403 explaining why not. */
    public ActorCapacity capacityFor(CurrentUser me, LeaveRequest r, LeaveEvent event) {
        return evaluate(me, r, event).orElseThrowIt();
    }

    public Optional<ActorCapacity> tryCapacity(CurrentUser me, LeaveRequest r, LeaveEvent event) {
        return evaluate(me, r, event).capacity();
    }

    private Verdict evaluate(CurrentUser me, LeaveRequest r, LeaveEvent event) {
        boolean owner = isOwner(me, r);
        switch (event) {
            case CANCEL -> {
                return owner ? Verdict.ok(ActorCapacity.OWNER)
                        : Verdict.deny(Errors.forbidden("NOT_OWNER", "Only the employee can cancel their own request"));
            }
            case MANAGER_APPROVE, MANAGER_REJECT -> {
                if (owner) {
                    return Verdict.deny(Errors.forbidden("NOT_AN_APPROVER", "You cannot decide your own request"));
                }
                if (!r.isManagerRoutedToHr() && me.id().equals(r.getManagerApproverId())) {
                    return Verdict.ok(ActorCapacity.ASSIGNED_MANAGER);
                }
                if (r.getStatus() == LeaveStatus.MANAGER_ESCALATED && me.id().equals(r.getEscalationApproverId())) {
                    return Verdict.ok(ActorCapacity.ESCALATION_MANAGER);
                }
                if (me.isHr() && (r.isEscalatedToHrPool() || r.isManagerRoutedToHr())) {
                    return Verdict.ok(ActorCapacity.HR_PROXY_MANAGER);
                }
                return Verdict.deny(Errors.forbidden("NOT_AN_APPROVER", "You are not the manager-stage approver"));
            }
            case HR_APPROVE, HR_REJECT -> {
                if (!me.isHr()) {
                    return Verdict.deny(Errors.forbidden("NOT_AN_APPROVER", "Only HR can decide at the HR stage"));
                }
                if (owner) {
                    return Verdict.deny(Errors.forbidden("NOT_AN_APPROVER", "You cannot decide your own request"));
                }
                if (me.id().equals(r.getManagerDecidedBy())) {
                    return Verdict.deny(Errors.forbidden("FOUR_EYES_VIOLATION",
                            "The manager-stage approver cannot also decide the HR stage"));
                }
                return Verdict.ok(ActorCapacity.HR);
            }
            default -> {
                return Verdict.deny(Errors.forbidden("FORBIDDEN", "Not allowed"));
            }
        }
    }

    public List<Long> managedTeamIds(CurrentUser me) {
        return org.teamIdsManagedBy(me.id());
    }

    private record Verdict(Optional<ActorCapacity> capacity, ApiException denial) {
        static Verdict ok(ActorCapacity c) { return new Verdict(Optional.of(c), null); }
        static Verdict deny(ApiException e) { return new Verdict(Optional.empty(), e); }
        ActorCapacity orElseThrowIt() {
            if (denial != null) throw denial;
            return capacity.orElseThrow();
        }
    }
}
