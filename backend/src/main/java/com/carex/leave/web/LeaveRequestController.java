package com.carex.leave.web;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.query.LeavePreviewService;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views.*;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.workflow.LeaveWorkflowService;
import com.carex.leave.workflow.LeaveWorkflowService.Decision;
import com.carex.leave.workflow.LeaveWorkflowService.DecisionCommand;
import com.carex.leave.workflow.LeaveWorkflowService.SubmitCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Employee-facing leave endpoints (+ decisions/cancel). No endpoint accepts an employee/actor id:
 * the principal comes from the JWT only.
 */
@RestController
@RequestMapping("/api/v1")
public class LeaveRequestController {
    private final LeaveWorkflowService workflow;
    private final LeaveQueryService queries;
    private final LeavePreviewService preview;
    private final BalanceService balances;
    private final BusinessCalendar calendar;

    public LeaveRequestController(LeaveWorkflowService workflow, LeaveQueryService queries, LeavePreviewService preview,
                                  BalanceService balances, BusinessCalendar calendar) {
        this.workflow = workflow;
        this.queries = queries;
        this.preview = preview;
        this.balances = balances;
        this.calendar = calendar;
    }

    public record CreateLeaveRequest(@NotBlank @Size(max = 20) String leaveTypeCode, @NotNull LocalDate startDate,
                                     @NotNull LocalDate endDate, @Size(max = 500) String reason,
                                     @NotNull UUID clientRequestId) {}

    public record PreviewRequest(@Size(max = 20) String leaveTypeCode, LocalDate startDate, LocalDate endDate) {}

    public record DecisionRequest(@NotNull Stage stage, @NotNull Decision decision, @Size(max = 1000) String comment,
                                  Boolean acknowledgeConflict) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    @GetMapping("/me/balances")
    public List<BalanceView> myBalances(@AuthenticationPrincipal CurrentUser me, @RequestParam(required = false) Integer year) {
        int y = year == null ? calendar.today().getYear() : year;
        // read-only: missing rows are shown with their computed pro-rated entitlement (created on first submit)
        return balances.snapshots(me.id(), y).stream().map(b -> new BalanceView(b.leaveTypeCode(), b.leaveTypeName(),
                b.year(), b.entitled(), b.adjustment(), b.used(), b.pending(), b.available(), b.prorationBasis())).toList();
    }

    @GetMapping("/me/leave-requests")
    public PageView<LeaveRequestSummary> myRequests(@AuthenticationPrincipal CurrentUser me,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return queries.mine(me, status, page, size);
    }

    @PostMapping("/leave-requests/preview")
    public PreviewResponse preview(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody PreviewRequest req) {
        return preview.preview(me, req.leaveTypeCode(), req.startDate(), req.endDate());
    }

    @PostMapping("/leave-requests")
    public ResponseEntity<LeaveRequestDetail> submit(@AuthenticationPrincipal CurrentUser me,
                                                     @Valid @RequestBody CreateLeaveRequest req) {
        var result = workflow.submit(me, new SubmitCommand(req.leaveTypeCode(), req.startDate(), req.endDate(),
                req.reason(), req.clientRequestId()));
        LeaveRequestDetail body = queries.detail(me, result.request().getId());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", String.valueOf(result.replayed()))
                .body(body);
    }

    @GetMapping("/leave-requests/{id}")
    public LeaveRequestDetail detail(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        return queries.detail(me, id);
    }

    @PostMapping("/leave-requests/{id}/decisions")
    public ResponseEntity<LeaveRequestDetail> decide(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                                     @Valid @RequestBody DecisionRequest req) {
        var result = workflow.decide(me, id, new DecisionCommand(req.stage(), req.decision(), req.comment(),
                Boolean.TRUE.equals(req.acknowledgeConflict())));
        return ResponseEntity.ok().header("Idempotent-Replay", String.valueOf(result.replayed()))
                .body(queries.detail(me, id));
    }

    @PostMapping("/leave-requests/{id}/cancel")
    public ResponseEntity<LeaveRequestDetail> cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                                     @Valid @RequestBody(required = false) CancelRequest req) {
        var result = workflow.cancel(me, id, req == null ? null : req.reason());
        return ResponseEntity.ok().header("Idempotent-Replay", String.valueOf(result.replayed()))
                .body(queries.detail(me, id));
    }
}
