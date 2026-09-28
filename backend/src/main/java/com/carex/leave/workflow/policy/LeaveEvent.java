package com.carex.leave.workflow.policy;

import com.carex.leave.leave.request.Stage;

/** implementation.md §7.2 */
public enum LeaveEvent {
    SUBMIT(Stage.NONE, "SUBMIT"),
    MANAGER_APPROVE(Stage.MANAGER, "APPROVE"),
    MANAGER_REJECT(Stage.MANAGER, "REJECT"),
    HR_APPROVE(Stage.HR, "APPROVE"),
    HR_REJECT(Stage.HR, "REJECT"),
    ESCALATE(Stage.NONE, "ESCALATE"),
    CANCEL(Stage.NONE, "CANCEL");

    private final Stage decisionStage;
    private final String actionName;

    LeaveEvent(Stage decisionStage, String actionName) {
        this.decisionStage = decisionStage;
        this.actionName = actionName;
    }

    public Stage decisionStage() { return decisionStage; }
    public String actionName() { return actionName; }
    public boolean isDecision() { return decisionStage != Stage.NONE; }
    public boolean isApprove() { return this == MANAGER_APPROVE || this == HR_APPROVE; }

    public static LeaveEvent decision(Stage stage, boolean approve) {
        return switch (stage) {
            case MANAGER -> approve ? MANAGER_APPROVE : MANAGER_REJECT;
            case HR -> approve ? HR_APPROVE : HR_REJECT;
            case NONE -> throw new IllegalArgumentException("No decision at stage NONE");
        };
    }
}
