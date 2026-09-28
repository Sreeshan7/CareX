package com.carex.leave.leave.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API DTOs (TypeScript mirror: frontend/src/api/types.ts). */
public final class Views {
    private Views() {}

    public record PersonRef(Long id, String name) {}

    public record EmployeeRef(Long id, String name, String teamName) {}

    public record TimelineEntry(Long id, String stage, String action, PersonRef actor, String actorCapacity,
                                String fromStatus, String toStatus, String comment, boolean conflictAcknowledged,
                                String channel, Instant at) {}

    public record EscalationView(Long id, Long requestId, String stage, Instant deadlineAt, Instant escalatedAt,
                                 PersonRef target, String targetRole, Instant resolvedAt, String resolution,
                                 long lateBySeconds, EmployeeRef employee, String requestStatus,
                                 LocalDate startDate, LocalDate endDate) {}

    public record DayView(LocalDate date, int absent, int teamSize, BigDecimal ratio, boolean over, List<String> people) {}

    public record ConflictView(boolean flagged, LocalDate peakDate, int peakAbsent, int teamSize,
                               BigDecimal thresholdPct, int minAbsent, List<DayView> days, Boolean snapshotFlagged,
                               String snapshotEvaluatedOn, String acknowledgedByManager, String acknowledgedByHr,
                               boolean namesVisible) {}

    public record LeaveRequestDetail(Long id, EmployeeRef employee, String leaveTypeCode, String leaveTypeName,
                                     LocalDate startDate, LocalDate endDate, BigDecimal workingDays, String reason,
                                     String status, String stage, Instant stageEnteredAt, Instant stageDeadlineAt,
                                     String channel, PersonRef managerApprover, boolean managerRoutedToHr,
                                     PersonRef escalationApprover, boolean escalatedToHrPool,
                                     PersonRef managerDecidedBy, PersonRef hrDecidedBy,
                                     List<EscalationView> escalations, ConflictView conflict,
                                     List<TimelineEntry> timeline, List<String> allowedActions,
                                     Instant createdAt, Instant decidedAt, Instant cancelledAt, long version) {}

    public record LeaveRequestSummary(Long id, EmployeeRef employee, String leaveTypeCode, LocalDate startDate,
                                      LocalDate endDate, BigDecimal workingDays, String status, String stage,
                                      Instant stageEnteredAt, Instant stageDeadlineAt, boolean escalated,
                                      boolean flagged, String channel, Instant createdAt,
                                      List<String> allowedActions) {}

    public record PageView<T>(List<T> items, int page, int size, long total) {}

    public record ExcludedDate(LocalDate date, String reason, String holidayName) {}

    public record ConflictPreview(boolean wouldFlag, LocalDate peakDate, int peakAbsent, int teamSize,
                                  BigDecimal thresholdPct) {}

    public record ErrorItem(String code, String message) {}

    public record PreviewResponse(boolean valid, int workingDays, List<LocalDate> workingDates,
                                  List<ExcludedDate> excludedDates, BigDecimal availableBefore,
                                  BigDecimal availableAfter, ConflictPreview conflict, List<ErrorItem> errors) {}

    public record BalanceView(String leaveTypeCode, String leaveTypeName, int year, BigDecimal entitled,
                              BigDecimal adjustment, BigDecimal used, BigDecimal pending, BigDecimal available,
                              String prorationBasis) {}
}
