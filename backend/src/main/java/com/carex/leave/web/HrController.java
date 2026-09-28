package com.carex.leave.web;

import com.carex.leave.audit.AuditAction;
import com.carex.leave.audit.AuditLog;
import com.carex.leave.audit.AuditLogRepository;
import com.carex.leave.audit.AuditService;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.dashboard.DashboardService;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views.EscalationView;
import com.carex.leave.leave.query.Views.LeaveRequestSummary;
import com.carex.leave.leave.query.Views.PageView;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.workflow.EscalationJob;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** /api/v1/hr/** — ROLE_HR enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/hr")
public class HrController {
    private final LeaveQueryService queries;
    private final DashboardService dashboard;
    private final EscalationJob escalationJob;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final OrgService org;
    private final BusinessCalendar calendar;

    public HrController(LeaveQueryService queries, DashboardService dashboard, EscalationJob escalationJob,
                        AuditLogRepository auditLogs, AuditService audit, OrgService org, BusinessCalendar calendar) {
        this.queries = queries;
        this.dashboard = dashboard;
        this.escalationJob = escalationJob;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.org = org;
        this.calendar = calendar;
    }

    @GetMapping("/approvals")
    public List<LeaveRequestSummary> approvals(@AuthenticationPrincipal CurrentUser me) {
        return queries.hrQueue(me);
    }

    @GetMapping("/escalations")
    public List<EscalationView> escalations(@AuthenticationPrincipal CurrentUser me,
                                            @RequestParam(defaultValue = "false") boolean open) {
        return queries.escalations(me, open);
    }

    @PostMapping("/escalations/run")
    public Map<String, Object> runEscalations(@AuthenticationPrincipal CurrentUser me) {
        int n = escalationJob.runOnce();
        audit.recordIsolated(me, null, AuditAction.ESCALATION_RUN_MANUAL, "ESCALATION", null,
                "Manual escalation run escalated " + n + " request(s)", Map.of("escalated", n));
        return Map.of("escalated", n);
    }

    @GetMapping("/leave-requests")
    public PageView<LeaveRequestSummary> search(@AuthenticationPrincipal CurrentUser me,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long teamId,
                                                @RequestParam(required = false) Long employeeId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) Boolean flagged,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return queries.hrSearch(me, status, teamId, employeeId, from, to, flagged, page, size);
    }

    @GetMapping("/conflicts")
    public List<LeaveRequestSummary> conflicts(@AuthenticationPrincipal CurrentUser me) {
        return queries.conflicts(me);
    }

    @GetMapping("/team-calendar")
    public LeaveQueryService.TeamCalendar calendar(@AuthenticationPrincipal CurrentUser me,
                                                   @RequestParam(required = false) Long teamId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queries.teamCalendar(me, from, to, teamId);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal CurrentUser me, @RequestParam(required = false) Integer year) {
        return dashboard.hrOverview(me, year == null ? calendar.today().getYear() : year);
    }

    @GetMapping("/balance-integrity")
    public Map<String, Object> integrity() {
        List<Map<String, Object>> mismatches = dashboard.balanceIntegrity();
        return Map.of("ok", mismatches.isEmpty(), "mismatches", mismatches);
    }

    @GetMapping("/teams")
    public List<Map<String, Object>> teams() {
        return org.allTeams().values().stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "name", t.getName())).toList();
    }

    public record AuditView(Long id, Instant occurredAt, Long actorId, String actorName, String actorRole, String channel,
                            String action, String entityType, Long entityId, String summary,
                            Map<String, Object> beforeState, Map<String, Object> afterState, String correlationId) {}

    @GetMapping("/audit")
    public PageView<AuditView> audit(@RequestParam(required = false) String entityType,
                                     @RequestParam(required = false) Long entityId,
                                     @RequestParam(required = false) Long actorId,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) ps.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) ps.add(cb.equal(root.get("entityId"), entityId));
            if (actorId != null) ps.add(cb.equal(root.get("actorId"), actorId));
            if (action != null && !action.isBlank()) ps.add(cb.equal(root.get("action"), action));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay(calendar.zone()).toInstant()));
            if (to != null) ps.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay(calendar.zone()).toInstant()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AuditLog> p = auditLogs.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, AppUser> users = org.usersById(p.getContent().stream().map(AuditLog::getActorId).filter(Objects::nonNull).toList());
        List<AuditView> items = p.getContent().stream().map(a -> new AuditView(a.getId(), a.getOccurredAt(), a.getActorId(),
                a.getActorId() == null ? "System" : users.containsKey(a.getActorId()) ? users.get(a.getActorId()).getFullName() : "#" + a.getActorId(),
                a.getActorRole(), a.getChannel(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getSummary(),
                a.getBeforeState(), a.getAfterState(), a.getCorrelationId())).toList();
        return new PageView<>(items, p.getNumber(), p.getSize(), p.getTotalElements());
    }
}
