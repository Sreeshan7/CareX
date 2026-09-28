package com.carex.leave.workflow.policy;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Server-computed buttons (implementation.md §14.3): state machine × access policy × business preconditions. */
@Service
public class AllowedActionsService {
    private static final List<LeaveEvent> USER_EVENTS = List.of(
            LeaveEvent.MANAGER_APPROVE, LeaveEvent.MANAGER_REJECT, LeaveEvent.HR_APPROVE, LeaveEvent.HR_REJECT,
            LeaveEvent.CANCEL);

    private final AccessPolicy policy;
    private final BusinessCalendar calendar;

    public AllowedActionsService(AccessPolicy policy, BusinessCalendar calendar) {
        this.policy = policy;
        this.calendar = calendar;
    }

    public List<String> allowedActions(CurrentUser me, LeaveRequest r) {
        List<String> out = new ArrayList<>();
        for (LeaveEvent e : USER_EVENTS) {
            if (!LeaveStateMachine.allows(r.getStatus(), e)) continue;
            if (policy.tryCapacity(me, r, e).isEmpty()) continue;
            if (e == LeaveEvent.CANCEL && r.getStatus() == LeaveStatus.APPROVED
                    && !r.getStartDate().isAfter(calendar.today())) continue;
            out.add(e.name());
        }
        return out;
    }
}
