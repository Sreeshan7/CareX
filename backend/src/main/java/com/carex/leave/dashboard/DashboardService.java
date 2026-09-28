package com.carex.leave.dashboard;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.conflict.ConflictFlag;
import com.carex.leave.conflict.ConflictFlagRepository;
import com.carex.leave.escalation.EscalationRepository;
import com.carex.leave.leave.balance.LeaveBalance;
import com.carex.leave.leave.balance.LeaveBalanceRepository;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views.LeaveRequestSummary;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.org.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** KPI aggregation for the Manager and HR dashboards. Read-only. */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final LeaveQueryService queries;
    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final ConflictFlagRepository flags;
    private final EscalationRepository escalations;
    private final OrgService org;
    private final BusinessCalendar calendar;

    public DashboardService(LeaveQueryService queries, LeaveRequestRepository requests, LeaveBalanceRepository balances,
                            ConflictFlagRepository flags, EscalationRepository escalations, OrgService org,
                            BusinessCalendar calendar) {
        this.queries = queries;
        this.requests = requests;
        this.balances = balances;
        this.flags = flags;
        this.escalations = escalations;
        this.org = org;
        this.calendar = calendar;
    }

    public Map<String, Object> manager(CurrentUser me) {
        List<LeaveRequestSummary> queue = queries.managerQueue(me);
        List<Long> teams = org.teamIdsManagedBy(me.id());
        LocalDate today = calendar.today();
        List<LeaveRequest> upcoming = teams.isEmpty() ? List.of()
                : requests.findTeamsOverlapping(teams, EnumSet.of(LeaveStatus.APPROVED), today, today.plusDays(6));
        long outToday = upcoming.stream().filter(r -> !r.getStartDate().isAfter(today) && !r.getEndDate().isBefore(today))
                .map(LeaveRequest::getEmployeeId).distinct().count();
        long teamSize = teams.stream().mapToLong(t -> org.activeMembers(t).size()).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", queue.size());
        m.put("escalated", queue.stream().filter(LeaveRequestSummary::escalated).count());
        m.put("flagged", queue.stream().filter(LeaveRequestSummary::flagged).count());
        m.put("outToday", outToday);
        m.put("outThisWeek", upcoming.stream().map(LeaveRequest::getEmployeeId).distinct().count());
        m.put("teamSize", teamSize);
        m.put("teams", teams.stream().map(t -> Map.of("id", t, "name", org.team(t).getName())).toList());
        m.put("queuePreview", queue.stream().limit(5).toList());
        return m;
    }

    public Map<String, Object> hrOverview(CurrentUser me, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        Map<Long, Team> teams = org.allTeams();
        List<LeaveRequest> all = requests.findAll().stream()
                .filter(r -> !r.getEndDate().isBefore(from) && !r.getStartDate().isAfter(to)).toList();
        Map<String, Long> byStatus = new TreeMap<>(all.stream().collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting())));
        Map<String, Long> byType = new TreeMap<>(all.stream().collect(Collectors.groupingBy(LeaveRequest::getLeaveTypeCode, Collectors.counting())));
        Map<String, BigDecimal> daysByTeam = new TreeMap<>(all.stream().filter(r -> LeaveStatus.ACTIVE.contains(r.getStatus()))
                .collect(Collectors.groupingBy(r -> teams.get(r.getTeamId()).getName(),
                        Collectors.reducing(BigDecimal.ZERO, LeaveRequest::getWorkingDays, BigDecimal::add))));
        Set<Long> activeIds = all.stream().filter(r -> LeaveStatus.ACTIVE.contains(r.getStatus())).map(LeaveRequest::getId)
                .collect(Collectors.toSet());
        long flaggedActive = flags.findByFlaggedTrue().stream().map(ConflictFlag::getRequestId).filter(activeIds::contains).count();
        double avgHours = all.stream().filter(r -> r.getStatus() == LeaveStatus.APPROVED && r.getDecidedAt() != null)
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getDecidedAt()).toMinutes()).average().orElse(0) / 60.0;
        LocalDate today = calendar.today();
        List<LeaveRequest> next14 = requests.findTeamsOverlapping(teams.keySet(), EnumSet.of(LeaveStatus.APPROVED), today, today.plusDays(13));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", year);
        m.put("totalRequests", all.size());
        m.put("byStatus", byStatus);
        m.put("byType", byType);
        m.put("activeDaysByTeam", daysByTeam);
        m.put("flaggedActive", flaggedActive);
        m.put("openEscalations", escalations.findByResolvedAtIsNullOrderByEscalatedAtDesc().size());
        m.put("pendingHr", queries.hrQueue(me).size());
        m.put("avgApprovalHours", Math.round(avgHours * 10) / 10.0);
        m.put("upcomingAbsences", queries.summaries(me, next14));
        m.put("headcount", teams.keySet().stream().mapToLong(t -> org.activeMembers(t).size()).sum());
        return m;
    }

    /** Recomputes pending/used from requests and compares with balance rows (implementation.md §9.2). Expected: []. */
    public List<Map<String, Object>> balanceIntegrity() {
        List<Map<String, Object>> mismatches = new ArrayList<>();
        Map<String, BigDecimal[]> expected = new LinkedHashMap<>();
        for (LeaveRequest r : requests.findAll()) {
            String key = r.getEmployeeId() + "|" + r.getLeaveTypeCode() + "|" + r.getStartDate().getYear();
            BigDecimal[] v = expected.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (r.getStatus().isPending()) v[0] = v[0].add(r.getWorkingDays());
            if (r.getStatus() == LeaveStatus.APPROVED) v[1] = v[1].add(r.getWorkingDays());
        }
        for (LeaveBalance b : balances.findAll()) {
            String key = b.getUserId() + "|" + b.getLeaveTypeCode() + "|" + b.getYear();
            BigDecimal[] v = expected.getOrDefault(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (v[0].compareTo(b.getPendingDays()) != 0 || v[1].compareTo(b.getUsedDays()) != 0) {
                AppUser u = org.user(b.getUserId());
                mismatches.add(Map.of("user", u.getFullName(), "type", b.getLeaveTypeCode(), "year", b.getYear(),
                        "pendingRow", b.getPendingDays(), "pendingExpected", v[0],
                        "usedRow", b.getUsedDays(), "usedExpected", v[1]));
            }
        }
        return mismatches;
    }
}
