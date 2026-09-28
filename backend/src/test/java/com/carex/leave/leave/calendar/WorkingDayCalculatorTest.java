package com.carex.leave.leave.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDayCalculatorTest {
    private static final Map<LocalDate, String> HOLIDAYS = Map.of(
            LocalDate.parse("2026-10-02"), "Gandhi Jayanti",
            LocalDate.parse("2026-10-03"), "Weekend holiday");

    @Test
    void excludesWeekendsAndHolidays() {
        // Mon 28 Sep .. Sun 4 Oct: Fri 2 Oct holiday, Sat/Sun weekend → 4 working days
        var b = WorkingDayCalculator.compute(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), HOLIDAYS);
        assertThat(b.count()).isEqualTo(4);
        assertThat(b.excluded()).extracting(WorkingDayCalculator.Excluded::reason)
                .containsExactly("HOLIDAY", "WEEKEND", "WEEKEND"); // holiday on Saturday counts as weekend
    }

    @Test
    void singleDay() {
        assertThat(WorkingDayCalculator.compute(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05"), HOLIDAYS).count()).isEqualTo(1);
    }

    @Test
    void weekendOnlyRangeHasNoWorkingDays() {
        assertThat(WorkingDayCalculator.compute(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11"), HOLIDAYS).count()).isZero();
    }
}
