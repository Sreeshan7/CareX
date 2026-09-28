package com.carex.leave.leave.request;

import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.error.Errors;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.type.LeaveType;
import com.carex.leave.org.AppUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Request validation rules 1–8 of implementation.md §9.5, in order (first failure wins on submit). */
@Component
public class LeaveRequestValidator {
    public static final int MAX_WORKING_DAYS = 30;
    private final BusinessCalendar calendar;
    private final WorkingDayCalculator workingDays;

    public LeaveRequestValidator(BusinessCalendar calendar, WorkingDayCalculator workingDays) {
        this.calendar = calendar;
        this.workingDays = workingDays;
    }

    public record Result(List<ApiException> errors, WorkingDayCalculator.Breakdown breakdown) {
        public boolean ok() { return errors.isEmpty(); }
    }

    public Result check(AppUser user, LeaveType type, LocalDate start, LocalDate end) {
        List<ApiException> errors = new ArrayList<>();
        LocalDate today = calendar.today();
        if (type == null || !type.isActive()) {
            errors.add(Errors.businessRule("INVALID_LEAVE_TYPE", "Unknown or inactive leave type"));
        }
        if (start == null || end == null) {
            errors.add(Errors.businessRule("INVALID_DATE_RANGE", "Start and end dates are required"));
            return new Result(errors, null);
        }
        if (end.isBefore(start)) {
            errors.add(Errors.businessRule("INVALID_DATE_RANGE", "End date must be on or after start date"));
            return new Result(errors, null);
        }
        if (start.getYear() != end.getYear()) {
            errors.add(Errors.businessRule("SPANS_YEARS", "Leave must fall within one calendar year — split it into two requests"));
        }
        int backdate = type == null ? 0 : type.getBackdateDaysAllowed();
        if (start.isBefore(today.minusDays(backdate))) {
            errors.add(Errors.businessRule("BACKDATED_NOT_ALLOWED", backdate == 0
                    ? "Leave cannot start in the past"
                    : "This leave type can start at most " + backdate + " days in the past"));
        }
        if (start.isBefore(user.getJoiningDate())) {
            errors.add(Errors.businessRule("BEFORE_JOINING_DATE", "Leave cannot start before your joining date " + user.getJoiningDate()));
        }
        if (end.isAfter(today.plusDays(365))) {
            errors.add(Errors.businessRule("TOO_FAR_IN_FUTURE", "Leave cannot end more than a year from today"));
        }
        WorkingDayCalculator.Breakdown bd = null;
        if (!end.isAfter(start.plusDays(400))) {
            bd = workingDays.breakdown(start, end);
            if (bd.count() < 1) {
                errors.add(Errors.businessRule("NO_WORKING_DAYS", "The selected dates contain no working days"));
            } else if (bd.count() > MAX_WORKING_DAYS) {
                errors.add(Errors.businessRule("REQUEST_TOO_LONG", "A single request can cover at most " + MAX_WORKING_DAYS + " working days"));
            }
        } else {
            errors.add(Errors.businessRule("REQUEST_TOO_LONG", "A single request can cover at most " + MAX_WORKING_DAYS + " working days"));
        }
        return new Result(errors, bd);
    }

    public WorkingDayCalculator.Breakdown validateOrThrow(AppUser user, LeaveType type, LocalDate start, LocalDate end) {
        Result r = check(user, type, start, end);
        if (!r.ok()) {
            throw r.errors().get(0);
        }
        return r.breakdown();
    }

    /** Trim, strip control characters, cap at 500 chars; empty → null. */
    public static String sanitizeReason(String reason) {
        if (reason == null) return null;
        String s = reason.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
