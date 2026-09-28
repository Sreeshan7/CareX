package com.carex.leave.leave.query;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.Errors;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.conflict.ConflictFlag;
import com.carex.leave.conflict.ConflictFlagRepository;
import com.carex.leave.conflict.ConflictResult;
import com.carex.leave.conflict.ConflictService;
import com.carex.leave.escalation.Escalation;
import com.carex.leave.escalation.EscalationRepository;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.query.Views.*;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.type.LeaveType;
import com.carex.leave.leave.type.LeaveTypeRepository;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.org.Team;
import com.carex.leave.workflow.history.ApprovalAction;
import com.carex.leave.workflow.history.ApprovalActionRepository;
import com.carex.leave.workflow.policy.AccessPolicy;
import com.carex.leave.workflow.policy.AllowedActionsService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read side. Every method takes the authenticated principal and applies {@link AccessPolicy} — the assistant's
 * query handlers call these same methods, so they inherit the same authorization.
 */
@Service
@Transactional(readOnly = true)
public class LeaveQueryService {
    private final LeaveRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final EscalationRepository escalations;
    private final ConflictFlagRepository flags;
    private final ConflictService conflicts;
    private final LeaveTypeRepository types;
    private final OrgService org;
    private final AccessPolicy policy;
    private final AllowedActionsService allowed;
    private final BusinessCalendar calendar;
    private final WorkingDayCalculator workingDayCalculator;

    public LeaveQueryService(LeaveRequestRepository requests, ApprovalActionRepository actions,
                             EscalationRepository escalations, ConflictFlagRepository flags,
                             ConflictService conflicts, LeaveTypeRepository types, OrgService org,
                             AccessPolicy policy, AllowedActionsService allowed, BusinessCalendar calendar,
                             WorkingDayCalculator workingDayCalculator) {
        this.workingDayCalculator = workingDayCalculator;
        this.requests = requests;
        this.actions = actions;
        this.escalations = escalations;
        this.flags = flags;
        this.conflicts = conflicts;
        this.types = types;
        this.org = org;
        this.policy = policy;
        this.allowed = allowed;
        this.calendar = calendar;
    }

    // ------------------------------------------------------------------ detail
    public LeaveRequestDetail detail(CurrentUser me, Long id) {
        LeaveRequest r = getVisible(me, id);
        Map<Long, AppUser> users = usersFor(List.of(r), actions.findByRequestIdOrderByIdAsc(r.getId()));
        List<TimelineEntry> timeline = actions.findByRequestIdOrderByIdAsc(r.getId()).stream()
                .map(a -> new TimelineEntry(a.getId(), a.getStage(), a.getAction(), ref(users, a.getActorId()),
                        a.getActorCapacity(), a.getFromStatus(), a.getToStatus(), a.getComment(),
                        a.isConflictAcknowledged(), a.getChannel(), a.getCreatedAt()))
                .toList();
        List<Escalation> escs = escalations.findByRequestIdOrderByIdAsc(r.getId());
        Map<Long, AppUser> escUsers = org.usersById(escs.stream().map(Escalation::getEscalatedToUserId)
                .filter(Objects::nonNull).toList());
        Team team = org.team(r.getTeamId());
        AppUser emp = users.get(r.getEmployeeId());
        LeaveType type = types.findById(r.getLeaveTypeCode()).orElse(null);
        return new LeaveRequestDetail(r.getId(), new EmployeeRef(emp.getId(), emp.getFullName(), team.getName()),
                r.getLeaveTypeCode(), type == null ? r.getLeaveTypeCode() : type.getDisplayName(),
                r.getStartDate(), r.getEndDate(), r.getWorkingDays(), r.getReason(), r.getStatus().name(),
                r.getStatus().stage().name(), r.getStageEnteredAt(), r.getStageDeadlineAt(), r.getChannel(),
                ref(users, r.getManagerApproverId()), r.isManagerRoutedToHr(), ref(users, r.getEscalationApproverId()),
                r.isEscalatedToHrPool(), ref(users, r.getManagerDecidedBy()), ref(users, r.getHrDecidedBy()),
                escs.stream().map(e -> escalationView(e, r, escUsers, users, team)).toList(),
                conflictView(me, r), timeline, allowed.allowedActions(me, r), r.getCreatedAt(), r.getDecidedAt(),
                r.getCancelledAt(), r.getVersion());
    }

    /** 404 for both "does not exist" and "not visible" — no existence oracle. */
    public LeaveRequest getVisible(CurrentUser me, Long id) {
        LeaveRequest r = requests.findById(id).orElseThrow(() -> Errors.notFound("Leave request not found"));
        policy.assertVisible(me, r);
        return r;
    }

    public ConflictView conflictView(CurrentUser me, LeaveRequest r) {
        boolean names = namesVisible(me, r);
        ConflictResult live = conflicts.evaluate(r);
        ConflictFlag snap = flags.findByRequestId(r.getId()).orElse(null);
        Set<Long> ids = new HashSet<>();
        if (names) live.days().forEach(d -> ids.addAll(d.employeeIds()));
        if (snap != null) {
            if (snap.getAcknowledgedByManager() != null) ids.add(snap.getAcknowledgedByManager());
            if (snap.getAcknowledgedByHr() != null) ids.add(snap.getAcknowledgedByHr());
        }
        Map<Long, AppUser> users = org.usersById(ids);
        List<DayView> days = live.days().stream().map(d -> new DayView(d.date(), d.absent(), d.teamSize(), d.ratio(),
                d.over(), names ? d.employeeIds().stream().map(uid -> name(users, uid)).toList() : null)).toList();
        return new ConflictView(live.flagged(), live.peakDate(), live.peakAbsent(), live.teamSize(), live.thresholdPct(),
                live.minAbsent(), days, snap == null ? null : snap.isFlagged(), snap == null ? null : snap.getEvaluatedOn(),
                snap == null || snap.getAcknowledgedByManager() == null ? null : name(users, snap.getAcknowledgedByManager()),
                snap == null || snap.getAcknowledgedByHr() == null ? null : name(users, snap.getAcknowledgedByHr()),
                names);
    }

    /** Employees see absence counts only; teammate names only for HR / the team's manager / the approvers. */
    private boolean namesVisible(CurrentUser me, LeaveRequest r) {
        return me.isHr() || me.id().equals(r.getManagerApproverId()) || me.id().equals(r.getEscalationApproverId())
                || (me.isManager() && org.teamIdsManagedBy(me.id()).contains(r.getTeamId()));
    }

    // ------------------------------------------------------------------ lists
    public PageView<LeaveRequestSummary> mine(CurrentUser me, String statusFilter, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<LeaveRequest> p;
        if (statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase("all")) {
            p = requests.findByEmployeeIdOrderByStartDateDesc(me.id(), pr);
        } else {
            p = requests.findByEmployeeIdAndStatusInOrderByStartDateDesc(me.id(), parseStatuses(statusFilter), pr);
        }
        return new PageView<>(summaries(me, p.getContent()), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    public List<LeaveRequestSummary> cancellable(CurrentUser me) {
        LocalDate today = calendar.today();
        List<LeaveRequest> rs = requests.findByEmployeeIdAndStatusInOrderByStartDateDesc(me.id(), LeaveStatus.ACTIVE,
                PageRequest.of(0, 100)).getContent().stream()
                .filter(r -> r.getStatus().isPending() || r.getStartDate().isAfter(today))
                .toList();
        return summaries(me, rs);
    }

    public List<LeaveRequestSummary> managerQueue(CurrentUser me) {
        List<LeaveRequest> rs = new ArrayList<>(requests.findManagerQueue(me.id(),
                EnumSet.of(LeaveStatus.PENDING_MANAGER, LeaveStatus.MANAGER_ESCALATED)));
        rs.removeIf(r -> allowed.allowedActions(me, r).isEmpty());
        return sortEscalatedFirst(summaries(me, rs));
    }

    public List<LeaveRequestSummary> decidedBy(CurrentUser me) {
        List<Long> ids = actions.findDecisionsBy(me.id()).stream().map(ApprovalAction::getRequestId).distinct().limit(50).toList();
        List<LeaveRequest> rs = requests.findByIdIn(ids).stream()
                .sorted(Comparator.comparing(LeaveRequest::getUpdatedAt).reversed()).toList();
        return summaries(me, rs);
    }

    public List<LeaveRequestSummary> hrQueue(CurrentUser me) {
        requireHr(me);
        List<LeaveRequest> rs = new ArrayList<>(requests.findHrQueue(me.id(),
                EnumSet.of(LeaveStatus.PENDING_HR, LeaveStatus.HR_ESCALATED),
                EnumSet.of(LeaveStatus.PENDING_MANAGER, LeaveStatus.MANAGER_ESCALATED)));
        return sortEscalatedFirst(summaries(me, rs));
    }

    public PageView<LeaveRequestSummary> hrSearch(CurrentUser me, String status, Long teamId, Long employeeId,
                                                  LocalDate from, LocalDate to, Boolean flagged, int page, int size) {
        requireHr(me);
        Set<Long> flaggedIds = Boolean.TRUE.equals(flagged)
                ? flags.findByFlaggedTrue().stream().map(ConflictFlag::getRequestId).collect(Collectors.toSet()) : null;
        Specification<LeaveRequest> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(root.get("status").in(parseStatuses(status)));
            if (teamId != null) ps.add(cb.equal(root.get("teamId"), teamId));
            if (employeeId != null) ps.add(cb.equal(root.get("employeeId"), employeeId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            if (flaggedIds != null) ps.add(flaggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(flaggedIds));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<LeaveRequest> p = requests.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageView<>(summaries(me, p.getContent()), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    /** Flagged active requests in the teams I manage (manager) or everywhere (HR). Live evaluation. */
    public List<LeaveRequestSummary> conflicts(CurrentUser me) {
        Collection<Long> teams = me.isHr() ? org.allTeams().keySet() : org.teamIdsManagedBy(me.id());
        if (teams.isEmpty()) return List.of();
        LocalDate today = calendar.today();
        List<LeaveRequest> rs = requests.findTeamsOverlapping(teams, LeaveStatus.ACTIVE, today, today.plusYears(1)).stream()
                .filter(r -> conflicts.evaluate(r).flagged())
                .toList();
        return summaries(me, rs);
    }

    public record CalendarEntry(Long requestId, Long employeeId, LocalDate startDate, LocalDate endDate, String status,
                                String leaveTypeCode, boolean flagged) {}

    public record CalendarMember(Long id, String name, Long teamId) {}

    public record CalendarDay(LocalDate date, boolean workingDay, int absent, int teamSize, boolean over) {}

    public record TeamCalendar(LocalDate from, LocalDate to, List<CalendarMember> members, List<CalendarEntry> entries,
                               Map<Long, List<CalendarDay>> daysByTeam, List<Map<String, Object>> teams) {}

    public TeamCalendar teamCalendar(CurrentUser me, LocalDate from, LocalDate to, Long teamFilter) {
        LocalDate f = from == null ? calendar.today() : from;
        LocalDate t = to == null ? f.plusDays(27) : to;
        if (t.isBefore(f) || t.isAfter(f.plusDays(62))) {
            throw Errors.badRequest("VALIDATION_FAILED", "Range must be 1..62 days");
        }
        List<Long> teams = me.isHr() ? new ArrayList<>(org.allTeams().keySet()) : org.teamIdsManagedBy(me.id());
        if (teamFilter != null) {
            if (!teams.contains(teamFilter)) throw Errors.notFound("Team not found");
            teams = List.of(teamFilter);
        }
        if (teams.isEmpty()) return new TeamCalendar(f, t, List.of(), List.of(), Map.of(), List.of());
        List<CalendarMember> members = new ArrayList<>();
        Map<Long, List<ConflictResult.Member>> membersByTeam = new java.util.HashMap<>();
        List<Map<String, Object>> teamInfo = new ArrayList<>();
        for (Long teamId : teams) {
            Team team = org.team(teamId);
            List<AppUser> ms = org.activeMembers(teamId);
            ms.forEach(u -> members.add(new CalendarMember(u.getId(), u.getFullName(), teamId)));
            membersByTeam.put(teamId, ms.stream().map(u -> new ConflictResult.Member(u.getId(), u.getJoiningDate())).toList());
            teamInfo.add(Map.of("id", teamId, "name", team.getName(), "thresholdPct", team.getConflictThresholdPct(),
                    "minAbsent", team.getConflictMinAbsent(), "size", ms.size()));
        }
        List<LeaveRequest> rs = requests.findTeamsOverlapping(teams, LeaveStatus.ACTIVE, f, t);
        Set<Long> flagged = flags.findByRequestIdIn(rs.stream().map(LeaveRequest::getId).toList()).stream()
                .filter(ConflictFlag::isFlagged).map(ConflictFlag::getRequestId).collect(Collectors.toSet());
        List<CalendarEntry> entries = rs.stream().map(r -> new CalendarEntry(r.getId(), r.getEmployeeId(), r.getStartDate(),
                r.getEndDate(), r.getStatus().name(), r.getLeaveTypeCode(), flagged.contains(r.getId()))).toList();
        Map<Long, List<CalendarDay>> daysByTeam = new java.util.HashMap<>();
        for (Long teamId : teams) {
            Team team = org.team(teamId);
            List<ConflictResult.Absence> abs = rs.stream().filter(r -> r.getTeamId().equals(teamId))
                    .map(r -> new ConflictResult.Absence(r.getId(), r.getEmployeeId(), r.getStartDate(), r.getEndDate())).toList();
            List<LocalDate> all = Stream.iterate(f, d -> !d.isAfter(t), d -> d.plusDays(1)).toList();
            var wd = new HashSet<>(workingDayCalculator.breakdown(f, t).workingDays());
            ConflictResult cr = ConflictResult.compute(all, abs, membersByTeam.get(teamId), team.getConflictThresholdPct(),
                    team.getConflictMinAbsent());
            daysByTeam.put(teamId, cr.days().stream().map(d -> new CalendarDay(d.date(), wd.contains(d.date()), d.absent(),
                    d.teamSize(), d.over() && wd.contains(d.date()))).toList());
        }
        return new TeamCalendar(f, t, members, entries, daysByTeam, teamInfo);
    }

    public List<EscalationView> escalations(CurrentUser me, boolean openOnly) {
        List<Escalation> escs = openOnly ? escalations.findByResolvedAtIsNullOrderByEscalatedAtDesc()
                : escalations.findAllByOrderByEscalatedAtDesc();
        Map<Long, LeaveRequest> reqs = requests.findByIdIn(escs.stream().map(Escalation::getRequestId).toList()).stream()
                .collect(Collectors.toMap(LeaveRequest::getId, Function.identity()));
        List<Escalation> visible = escs.stream().filter(e -> {
            LeaveRequest r = reqs.get(e.getRequestId());
            if (r == null) return false;
            if (me.isHr()) return true;
            return me.id().equals(e.getEscalatedToUserId()) || me.id().equals(r.getManagerApproverId());
        }).toList();
        Set<Long> ids = new HashSet<>();
        visible.forEach(e -> {
            if (e.getEscalatedToUserId() != null) ids.add(e.getEscalatedToUserId());
            ids.add(reqs.get(e.getRequestId()).getEmployeeId());
        });
        Map<Long, AppUser> users = org.usersById(ids);
        Map<Long, Team> teams = org.allTeams();
        return visible.stream().map(e -> {
            LeaveRequest r = reqs.get(e.getRequestId());
            return escalationView(e, r, users, users, teams.get(r.getTeamId()));
        }).toList();
    }

    // ------------------------------------------------------------------ helpers
    public List<LeaveRequestSummary> summaries(CurrentUser me, List<LeaveRequest> rs) {
        if (rs.isEmpty()) return List.of();
        Map<Long, AppUser> users = org.usersById(rs.stream().map(LeaveRequest::getEmployeeId).collect(Collectors.toSet()));
        Map<Long, Team> teams = org.allTeams();
        Set<Long> flagged = flags.findByRequestIdIn(rs.stream().map(LeaveRequest::getId).toList()).stream()
                .filter(ConflictFlag::isFlagged).map(ConflictFlag::getRequestId).collect(Collectors.toSet());
        return rs.stream().map(r -> {
            AppUser u = users.get(r.getEmployeeId());
            return new LeaveRequestSummary(r.getId(),
                    new EmployeeRef(u.getId(), u.getFullName(), teams.get(r.getTeamId()).getName()),
                    r.getLeaveTypeCode(), r.getStartDate(), r.getEndDate(), r.getWorkingDays(), r.getStatus().name(),
                    r.getStatus().stage().name(), r.getStageEnteredAt(), r.getStageDeadlineAt(), r.getStatus().isEscalated(),
                    flagged.contains(r.getId()), r.getChannel(), r.getCreatedAt(), allowed.allowedActions(me, r));
        }).toList();
    }

    private static List<LeaveRequestSummary> sortEscalatedFirst(List<LeaveRequestSummary> list) {
        return list.stream().sorted(Comparator.comparing((LeaveRequestSummary s) -> !s.escalated())
                .thenComparing(LeaveRequestSummary::stageEnteredAt)).toList();
    }

    private EscalationView escalationView(Escalation e, LeaveRequest r, Map<Long, AppUser> targetUsers,
                                          Map<Long, AppUser> users, Team team) {
        AppUser emp = users.containsKey(r.getEmployeeId()) ? users.get(r.getEmployeeId()) : org.user(r.getEmployeeId());
        return new EscalationView(e.getId(), e.getRequestId(), e.getStage(), e.getDeadlineAt(), e.getEscalatedAt(),
                ref(targetUsers, e.getEscalatedToUserId()), e.getEscalatedToRole(), e.getResolvedAt(), e.getResolution(),
                Math.max(0, e.getEscalatedAt().getEpochSecond() - e.getDeadlineAt().getEpochSecond()),
                new EmployeeRef(emp.getId(), emp.getFullName(), team.getName()), r.getStatus().name(),
                r.getStartDate(), r.getEndDate());
    }

    private Map<Long, AppUser> usersFor(List<LeaveRequest> rs, List<ApprovalAction> acts) {
        Set<Long> ids = new HashSet<>();
        for (LeaveRequest r : rs) {
            Stream.of(r.getEmployeeId(), r.getManagerApproverId(), r.getEscalationApproverId(), r.getManagerDecidedBy(),
                    r.getHrDecidedBy()).filter(Objects::nonNull).forEach(ids::add);
        }
        acts.stream().map(ApprovalAction::getActorId).filter(Objects::nonNull).forEach(ids::add);
        return org.usersById(ids);
    }

    private static PersonRef ref(Map<Long, AppUser> users, Long id) {
        if (id == null) return null;
        AppUser u = users.get(id);
        return new PersonRef(id, u == null ? "#" + id : u.getFullName());
    }

    private static String name(Map<Long, AppUser> users, Long id) {
        AppUser u = users.get(id);
        return u == null ? "#" + id : u.getFullName();
    }

    private static Set<LeaveStatus> parseStatuses(String csv) {
        Set<LeaveStatus> out = EnumSet.noneOf(LeaveStatus.class);
        for (String s : csv.split(",")) {
            String v = s.trim().toUpperCase();
            if (v.isEmpty()) continue;
            switch (v) {
                case "PENDING" -> out.addAll(LeaveStatus.PENDING);
                case "ACTIVE" -> out.addAll(LeaveStatus.ACTIVE);
                default -> {
                    try {
                        out.add(LeaveStatus.valueOf(v));
                    } catch (IllegalArgumentException e) {
                        throw Errors.badRequest("VALIDATION_FAILED", "Unknown status " + v);
                    }
                }
            }
        }
        return out;
    }

    private static void requireHr(CurrentUser me) {
        if (!me.isHr()) throw Errors.forbidden("FORBIDDEN", "HR only");
    }
}
