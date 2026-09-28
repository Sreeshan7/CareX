package com.carex.leave.assistant.core;

import com.carex.leave.org.Role;

import java.util.EnumSet;
import java.util.Set;

import static com.carex.leave.assistant.core.Intent.*;

/** Role → allowed assistant intents (implementation.md §18.9). Data, unit-tested. */
public final class IntentPolicy {
    private static final Set<Intent> EMPLOYEE = EnumSet.of(QUERY_BALANCE, QUERY_MY_REQUESTS, QUERY_REQUEST_STATUS,
            APPLY_LEAVE, CANCEL_LEAVE, QUERY_HOLIDAYS, POLICY_HELP, GREETING, UNKNOWN);
    private static final Set<Intent> MANAGER;
    private static final Set<Intent> HR;

    static {
        MANAGER = EnumSet.copyOf(EMPLOYEE);
        MANAGER.addAll(EnumSet.of(QUERY_PENDING_APPROVALS, QUERY_TEAM_LEAVE, QUERY_CONFLICTS, APPROVE_REQUEST,
                REJECT_REQUEST, QUERY_ESCALATIONS));
        HR = EnumSet.copyOf(MANAGER);
        HR.add(QUERY_LEAVE_OVERVIEW);
    }

    private IntentPolicy() {}

    public static Set<Intent> allowed(Role role) {
        return switch (role) {
            case EMPLOYEE -> EMPLOYEE;
            case MANAGER -> MANAGER;
            case HR -> HR;
        };
    }

    public static boolean isAllowed(Role role, Intent intent) {
        return allowed(role).contains(intent);
    }
}
