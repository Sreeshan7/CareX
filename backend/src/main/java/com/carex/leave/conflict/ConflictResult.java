package com.carex.leave.conflict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Team absence evaluation (implementation.md §10.2). Pure computation: flagged when on any working day
 * absent ≥ minAbsent AND absent/teamSize > threshold%.
 */
public record ConflictResult(boolean flagged, LocalDate peakDate, int peakAbsent, int teamSize,
                             BigDecimal thresholdPct, int minAbsent, List<DayCount> days,
                             List<Long> overlappingRequestIds) {

    public record DayCount(LocalDate date, int absent, int teamSize, BigDecimal ratio, boolean over,
                           List<Long> requestIds, List<Long> employeeIds) {}

    public record Absence(Long requestId, Long employeeId, LocalDate start, LocalDate end) {}

    public record Member(Long id, LocalDate joiningDate) {}

    public static ConflictResult compute(List<LocalDate> workingDays, List<Absence> absences, List<Member> members,
                                         BigDecimal thresholdPct, int minAbsent) {
        List<DayCount> days = new ArrayList<>();
        Set<Long> overlapping = new LinkedHashSet<>();
        boolean flagged = false;
        DayCount peak = null;
        for (LocalDate d : workingDays) {
            Set<Long> employees = new TreeSet<>();
            List<Long> requestIds = new ArrayList<>();
            for (Absence a : absences) {
                if (!d.isBefore(a.start()) && !d.isAfter(a.end())) {
                    if (employees.add(a.employeeId()) && a.requestId() != null) {
                        requestIds.add(a.requestId());
                    }
                }
            }
            int size = (int) members.stream().filter(m -> !m.joiningDate().isAfter(d)).count();
            int absent = employees.size();
            BigDecimal ratio = size == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(absent).divide(BigDecimal.valueOf(size), 4, RoundingMode.HALF_UP);
            // absent/size > pct/100  ⇔  absent*100 > pct*size (exact, no rounding)
            boolean over = size > 0 && absent >= minAbsent
                    && BigDecimal.valueOf(absent * 100L).compareTo(thresholdPct.multiply(BigDecimal.valueOf(size))) > 0;
            DayCount dc = new DayCount(d, absent, size, ratio, over, requestIds, List.copyOf(employees));
            days.add(dc);
            if (over) {
                flagged = true;
            }
            if (absent > 0) {
                overlapping.addAll(requestIds);
            }
            if (peak == null || ratio.compareTo(peak.ratio()) > 0) {
                peak = dc;
            }
        }
        days.sort(Comparator.comparing(DayCount::date));
        return new ConflictResult(flagged, peak == null ? null : peak.date(), peak == null ? 0 : peak.absent(),
                peak == null ? members.size() : peak.teamSize(), thresholdPct, minAbsent, days, List.copyOf(overlapping));
    }
}
