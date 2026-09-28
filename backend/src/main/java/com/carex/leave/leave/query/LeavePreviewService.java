package com.carex.leave.leave.query;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.conflict.ConflictResult;
import com.carex.leave.conflict.ConflictService;
import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.balance.LeaveBalance;
import com.carex.leave.leave.query.Views.*;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.LeaveRequestValidator;
import com.carex.leave.leave.request.LeaveStatus;
import com.carex.leave.leave.type.LeaveType;
import com.carex.leave.leave.type.LeaveTypeRepository;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Side-effect-free validation + working days + balance-after + conflict preview (implementation.md §9.6).
 * Used by the Apply form and the assistant's confirmation card. Takes no locks and writes no leave data.
 */
@Service
public class LeavePreviewService {
    private final LeaveRequestValidator validator;
    private final LeaveTypeRepository types;
    private final OrgService org;
    private final BalanceService balances;
    private final LeaveRequestRepository requests;
    private final ConflictService conflicts;

    public LeavePreviewService(LeaveRequestValidator validator, LeaveTypeRepository types, OrgService org,
                               BalanceService balances, LeaveRequestRepository requests, ConflictService conflicts) {
        this.validator = validator;
        this.types = types;
        this.org = org;
        this.balances = balances;
        this.requests = requests;
        this.conflicts = conflicts;
    }

    @Transactional
    public PreviewResponse preview(CurrentUser me, String typeCode, LocalDate start, LocalDate end) {
        AppUser user = org.user(me.id());
        LeaveType type = typeCode == null ? null : types.findById(typeCode).orElse(null);
        LeaveRequestValidator.Result v = validator.check(user, type, start, end);
        List<ErrorItem> errors = new ArrayList<>(v.errors().stream().map(ApiPreview::item).toList());
        int days = v.breakdown() == null ? 0 : v.breakdown().count();
        List<LocalDate> workingDates = v.breakdown() == null ? List.of() : v.breakdown().workingDays();
        List<ExcludedDate> excluded = v.breakdown() == null ? List.of() : v.breakdown().excluded().stream()
                .map(x -> new ExcludedDate(x.date(), x.reason(), x.holidayName())).toList();

        BigDecimal before = null;
        BigDecimal after = null;
        if (type != null && start != null) {
            Optional<LeaveBalance> b = balances.peek(me.id(), type.getCode(), start.getYear());
            if (b.isPresent()) {
                before = b.get().available();
                after = before.subtract(BigDecimal.valueOf(days));
                if (days > 0 && after.signum() < 0) {
                    errors.add(new ErrorItem("INSUFFICIENT_BALANCE", "Available " + before + " days, requested " + days));
                }
            }
        }
        if (start != null && end != null && !end.isBefore(start)
                && !requests.findOverlappingForEmployee(me.id(), LeaveStatus.ACTIVE, start, end).isEmpty()) {
            errors.add(new ErrorItem("OVERLAPPING_REQUEST", "You already have leave on some of these dates"));
        }
        ConflictPreview cp = null;
        if (days > 0) {
            ConflictResult cr = conflicts.evaluateProspective(me.id(), user.getTeamId(), start, end);
            cp = new ConflictPreview(cr.flagged(), cr.peakDate(), cr.peakAbsent(), cr.teamSize(), cr.thresholdPct());
        }
        return new PreviewResponse(errors.isEmpty(), days, workingDates, excluded, before, after, cp, errors);
    }

    private static final class ApiPreview {
        static ErrorItem item(ApiException e) {
            return new ErrorItem(e.getCode(), e.getMessage());
        }
    }
}
