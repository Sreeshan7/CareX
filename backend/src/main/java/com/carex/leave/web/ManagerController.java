package com.carex.leave.web;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.dashboard.DashboardService;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views.EscalationView;
import com.carex.leave.leave.query.Views.LeaveRequestSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** /api/v1/manager/** — ROLE_MANAGER enforced in SecurityConfig; scope derived from the principal. */
@RestController
@RequestMapping("/api/v1/manager")
public class ManagerController {
    private final LeaveQueryService queries;
    private final DashboardService dashboard;

    public ManagerController(LeaveQueryService queries, DashboardService dashboard) {
        this.queries = queries;
        this.dashboard = dashboard;
    }

    @GetMapping("/approvals")
    public List<LeaveRequestSummary> approvals(@AuthenticationPrincipal CurrentUser me,
                                               @RequestParam(defaultValue = "pending") String scope) {
        return "decided".equalsIgnoreCase(scope) ? queries.decidedBy(me) : queries.managerQueue(me);
    }

    @GetMapping("/team")
    public LeaveQueryService.TeamCalendar team(@AuthenticationPrincipal CurrentUser me,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queries.teamCalendar(me, from, to, null);
    }

    @GetMapping("/conflicts")
    public List<LeaveRequestSummary> conflicts(@AuthenticationPrincipal CurrentUser me) {
        return queries.conflicts(me);
    }

    @GetMapping("/escalations")
    public List<EscalationView> escalations(@AuthenticationPrincipal CurrentUser me) {
        return queries.escalations(me, false);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal CurrentUser me) {
        return dashboard.manager(me);
    }
}
