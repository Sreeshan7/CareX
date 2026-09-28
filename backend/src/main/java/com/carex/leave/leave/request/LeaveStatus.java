package com.carex.leave.leave.request;

import java.util.EnumSet;
import java.util.Set;

/** implementation.md §7.1 */
public enum LeaveStatus {
    PENDING_MANAGER(Stage.MANAGER),
    MANAGER_ESCALATED(Stage.MANAGER),
    PENDING_HR(Stage.HR),
    HR_ESCALATED(Stage.HR),
    APPROVED(Stage.NONE),
    REJECTED(Stage.NONE),
    CANCELLED(Stage.NONE);

    /** Statuses that occupy dates / count toward team absence (mirrors the partial exclusion constraint). */
    public static final Set<LeaveStatus> ACTIVE = EnumSet.of(PENDING_MANAGER, MANAGER_ESCALATED, PENDING_HR, HR_ESCALATED, APPROVED);
    /** Statuses that hold a pending balance reservation. */
    public static final Set<LeaveStatus> PENDING = EnumSet.of(PENDING_MANAGER, MANAGER_ESCALATED, PENDING_HR, HR_ESCALATED);

    private final Stage stage;

    LeaveStatus(Stage stage) {
        this.stage = stage;
    }

    public Stage stage() { return stage; }
    public boolean isPending() { return PENDING.contains(this); }
    public boolean isEscalated() { return this == MANAGER_ESCALATED || this == HR_ESCALATED; }
}
