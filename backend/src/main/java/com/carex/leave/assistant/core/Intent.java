package com.carex.leave.assistant.core;

import java.util.EnumSet;
import java.util.Set;

/** implementation.md §18.3 */
public enum Intent {
    APPLY_LEAVE, CANCEL_LEAVE, APPROVE_REQUEST, REJECT_REQUEST,
    QUERY_BALANCE, QUERY_MY_REQUESTS, QUERY_REQUEST_STATUS, QUERY_PENDING_APPROVALS, QUERY_TEAM_LEAVE,
    QUERY_CONFLICTS, QUERY_ESCALATIONS, QUERY_LEAVE_OVERVIEW, QUERY_HOLIDAYS, POLICY_HELP, GREETING, UNKNOWN;

    public static final Set<Intent> MUTATIONS = EnumSet.of(APPLY_LEAVE, CANCEL_LEAVE, APPROVE_REQUEST, REJECT_REQUEST);

    public boolean isMutation() { return MUTATIONS.contains(this); }

    public static Intent parse(String s) {
        if (s == null) return UNKNOWN;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
