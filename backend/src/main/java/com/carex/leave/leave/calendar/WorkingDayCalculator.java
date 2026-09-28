package com.carex.leave.leave.calendar;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Assumption A4: working days = Mon–Fri minus the holiday table. Whole days only. */
@Component
public class WorkingDayCalculator {
    private final HolidayRepository holidays;

    public WorkingDayCalculator(HolidayRepository holidays) {
        this.holidays = holidays;
    }

    public Breakdown breakdown(LocalDate start, LocalDate end) {
        Map<LocalDate, String> hol = holidaysBetween(start, end);
        return compute(start, end, hol);
    }

    public int count(LocalDate start, LocalDate end) {
        return breakdown(start, end).workingDays().size();
    }

    /** End date after {@code n} working days starting at {@code start} (inclusive; start moved to next working day). */
    public LocalDate addWorkingDays(LocalDate start, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        Map<LocalDate, String> hol = holidaysBetween(start, start.plusDays(n * 3L + 30));
        LocalDate d = start;
        int counted = 0;
        while (true) {
            if (isWorking(d, hol)) {
                counted++;
                if (counted == n) {
                    return d;
                }
            }
            d = d.plusDays(1);
        }
    }

    public LocalDate nextWorkingDayOnOrAfter(LocalDate date) {
        Map<LocalDate, String> hol = holidaysBetween(date, date.plusDays(30));
        LocalDate d = date;
        while (!isWorking(d, hol)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public List<Holiday> upcomingHolidays(LocalDate from, int limit) {
        return holidays.findByHolidayDateBetweenOrderByHolidayDate(from, from.plusYears(1)).stream().limit(limit).toList();
    }

    /** Pure computation (unit-testable without a database). */
    public static Breakdown compute(LocalDate start, LocalDate end, Map<LocalDate, String> hol) {
        List<LocalDate> working = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWeekend(d)) {
                excluded.add(new Excluded(d, "WEEKEND", null));
            } else if (hol.containsKey(d)) {
                excluded.add(new Excluded(d, "HOLIDAY", hol.get(d)));
            } else {
                working.add(d);
            }
        }
        return new Breakdown(working, excluded);
    }

    private Map<LocalDate, String> holidaysBetween(LocalDate from, LocalDate to) {
        return holidays.findByHolidayDateBetweenOrderByHolidayDate(from, to).stream()
                .collect(Collectors.toMap(Holiday::getHolidayDate, Holiday::getName));
    }

    private static boolean isWorking(LocalDate d, Map<LocalDate, String> hol) {
        return !isWeekend(d) && !hol.containsKey(d);
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public record Excluded(LocalDate date, String reason, String holidayName) {}

    public record Breakdown(List<LocalDate> workingDays, List<Excluded> excluded) {
        public int count() { return workingDays.size(); }
    }
}
