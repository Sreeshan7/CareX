package com.carex.leave.leave.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable after submit except for workflow fields. Workflow fields are changed ONLY by LeaveWorkflowService
 * (ArchUnit-enforced: no other class may call the mutators below).
 */
@Entity
@Table(name = "leave_request")
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "leave_type_code", nullable = false, updatable = false)
    private String leaveTypeCode;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "working_days", nullable = false, updatable = false)
    private BigDecimal workingDays;

    @Column(updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "manager_approver_id", nullable = false, updatable = false)
    private Long managerApproverId;

    @Column(name = "manager_routed_to_hr", nullable = false, updatable = false)
    private boolean managerRoutedToHr;

    @Column(name = "escalation_approver_id")
    private Long escalationApproverId;

    @Column(name = "escalated_to_hr_pool", nullable = false)
    private boolean escalatedToHrPool;

    @Column(name = "manager_decided_by")
    private Long managerDecidedBy;

    @Column(name = "hr_decided_by")
    private Long hrDecidedBy;

    @Column(name = "stage_entered_at", nullable = false)
    private Instant stageEnteredAt;

    @Column(name = "stage_deadline_at")
    private Instant stageDeadlineAt;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(nullable = false, updatable = false)
    private String channel;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected LeaveRequest() {}

    public LeaveRequest(Long employeeId, String leaveTypeCode, LocalDate startDate, LocalDate endDate,
                        BigDecimal workingDays, String reason, Long teamId, Long managerApproverId,
                        boolean managerRoutedToHr, UUID clientRequestId, String channel, Instant now,
                        Instant firstDeadline) {
        this.employeeId = employeeId;
        this.leaveTypeCode = leaveTypeCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workingDays = workingDays;
        this.reason = reason;
        this.teamId = teamId;
        this.managerApproverId = managerApproverId;
        this.managerRoutedToHr = managerRoutedToHr;
        this.clientRequestId = clientRequestId;
        this.channel = channel;
        this.status = LeaveStatus.PENDING_MANAGER;
        this.stageEnteredAt = now;
        this.stageDeadlineAt = firstDeadline;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ---- workflow mutators (LeaveWorkflowService only) ----
    public void transitionTo(LeaveStatus next, Instant now) {
        if (next.stage() != status.stage() || next.stage() == Stage.NONE) {
            this.stageEnteredAt = now;
        }
        this.status = next;
        this.updatedAt = now;
    }

    public void setStageDeadlineAt(Instant deadline) { this.stageDeadlineAt = deadline; }
    public void setEscalationApproverId(Long id) { this.escalationApproverId = id; }
    public void setEscalatedToHrPool(boolean v) { this.escalatedToHrPool = v; }
    public void setManagerDecidedBy(Long id) { this.managerDecidedBy = id; }
    public void setHrDecidedBy(Long id) { this.hrDecidedBy = id; }
    public void setDecidedAt(Instant t) { this.decidedAt = t; }
    public void setCancelledAt(Instant t) { this.cancelledAt = t; }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getWorkingDays() { return workingDays; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public Long getTeamId() { return teamId; }
    public Long getManagerApproverId() { return managerApproverId; }
    public boolean isManagerRoutedToHr() { return managerRoutedToHr; }
    public Long getEscalationApproverId() { return escalationApproverId; }
    public boolean isEscalatedToHrPool() { return escalatedToHrPool; }
    public Long getManagerDecidedBy() { return managerDecidedBy; }
    public Long getHrDecidedBy() { return hrDecidedBy; }
    public Instant getStageEnteredAt() { return stageEnteredAt; }
    public Instant getStageDeadlineAt() { return stageDeadlineAt; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getChannel() { return channel; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public String toString() { // never includes the reason text (may be sensitive)
        return "LeaveRequest[id=" + id + ", status=" + status + "]";
    }
}
