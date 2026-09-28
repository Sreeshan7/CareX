package com.carex.leave.conflict;

import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.request.LeaveRequest;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.org.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Team-wide conflict detection. Evaluated at submit (under the team advisory lock), at every approval decision,
 * and live on read. Flags NEVER block, reject or change status (mandatory requirement M7).
 */
@Service
public class ConflictService {
    private final LeaveRequestRepository requests;
    private final ConflictFlagRepository flags;
    private final OrgService org;
    private final WorkingDayCalculator workingDays;
    private final BusinessCalendar calendar;

    public ConflictService(LeaveRequestRepository requests, ConflictFlagRepository flags, OrgService org,
                           WorkingDayCalculator workingDays, BusinessCalendar calendar) {
        this.requests = requests;
        this.flags = flags;
        this.org = org;
        this.workingDays = workingDays;
        this.calendar = calendar;
    }

    /** Live evaluation of an existing (active or historical) request. The request itself counts if active. */
    @Transactional
    public ConflictResult evaluate(LeaveRequest r) {
        List<ConflictResult.Absence> absences = teamAbsences(r.getTeamId(), r.getStartDate(), r.getEndDate());
        if (!LeaveStatus.ACTIVE.contains(r.getStatus())) {
            absences.add(new ConflictResult.Absence(r.getId(), r.getEmployeeId(), r.getStartDate(), r.getEndDate()));
        }
        return compute(r.getTeamId(), r.getStartDate(), r.getEndDate(), absences);
    }

    /** "What if" evaluation for the preview endpoint (no request persisted yet). */
    @Transactional
    public ConflictResult evaluateProspective(Long employeeId, Long teamId, LocalDate start, LocalDate end) {
        List<ConflictResult.Absence> absences = teamAbsences(teamId, start, end);
        absences.add(new ConflictResult.Absence(null, employeeId, start, end));
        return compute(teamId, start, end, absences);
    }

    /** Persist/refresh the snapshot (called inside workflow transactions). */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConflictFlag evaluateAndPersist(LeaveRequest r, String evaluatedOn) {
        ConflictResult result = evaluate(r);
        ConflictFlag flag = flags.findByRequestId(r.getId()).orElseGet(() -> new ConflictFlag(r.getId()));
        flag.apply(result, calendar.now(), evaluatedOn);
        return flags.save(flag);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acknowledge(ConflictFlag flag, Long userId, Stage stage) {
        if (stage == Stage.MANAGER) {
            flag.setAcknowledgedByManager(userId);
        } else if (stage == Stage.HR) {
            flag.setAcknowledgedByHr(userId);
        }
        flags.save(flag);
    }

    public Optional<ConflictFlag> snapshot(Long requestId) {
        return flags.findByRequestId(requestId);
    }

    private List<ConflictResult.Absence> teamAbsences(Long teamId, LocalDate start, LocalDate end) {
        List<ConflictResult.Absence> out = new ArrayList<>();
        for (LeaveRequest o : requests.findTeamOverlapping(teamId, LeaveStatus.ACTIVE, start, end)) {
            out.add(new ConflictResult.Absence(o.getId(), o.getEmployeeId(), o.getStartDate(), o.getEndDate()));
        }
        return out;
    }

    private ConflictResult compute(Long teamId, LocalDate start, LocalDate end, List<ConflictResult.Absence> absences) {
        Team team = org.team(teamId);
        List<ConflictResult.Member> members = org.activeMembers(teamId).stream()
                .map((AppUser u) -> new ConflictResult.Member(u.getId(), u.getJoiningDate()))
                .toList();
        List<LocalDate> days = workingDays.breakdown(start, end).workingDays();
        return ConflictResult.compute(days, absences, members, team.getConflictThresholdPct(), team.getConflictMinAbsent());
    }
}
