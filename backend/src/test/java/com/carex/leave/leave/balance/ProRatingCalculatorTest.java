package com.carex.leave.leave.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Every row of the pro-rating table in implementation.md §9.3. */
class ProRatingCalculatorTest {

    @ParameterizedTest(name = "{0}: E={1} joined {2} → {3} months, {4}")
    @CsvSource({
            "joined previous year, 18, 2025-03-10, 12, 18.0",
            "1 Jan joiner,          18, 2026-01-01, 12, 18.0",
            "mid-July on/before 15, 18, 2026-07-10, 6,  9.0",
            "Farhan after 15th,     18, 2026-07-20, 5,  7.5",
            "casual 16 Apr,          8, 2026-04-16, 8,  5.5",
            "casual 20 Aug,          8, 2026-08-20, 4,  2.5",
            "sick 1 Jun,            10, 2026-06-01, 7,  6.0",
            "15 Dec exactly,        18, 2026-12-15, 1,  1.5",
            "16 Dec,                18, 2026-12-16, 0,  0.0",
            "future joiner 1 Oct,   18, 2026-10-01, 3,  4.5",
    })
    void blueprintTable(String name, String annual, String joined, int months, String expected) {
        var r = ProRatingCalculator.entitlement(new BigDecimal(annual), true, LocalDate.parse(joined), 2026).orElseThrow();
        assertThat(r.entitled()).isEqualByComparingTo(expected);
        assertThat(r.monthsEligible()).isEqualTo(months);
        assertThat(r.basis()).isNotBlank();
    }

    @Test
    void nonProratedTypeGetsFullEntitlement() {
        var r = ProRatingCalculator.entitlement(new BigDecimal("10"), false, LocalDate.parse("2026-11-20"), 2026).orElseThrow();
        assertThat(r.entitled()).isEqualByComparingTo("10.0");
    }

    @Test
    void joiningAfterYearEndHasNoRow() {
        assertThat(ProRatingCalculator.entitlement(new BigDecimal("18"), true, LocalDate.parse("2027-01-05"), 2026)).isEmpty();
    }

    @Test
    void roundingToNearestHalfTiesUp() {
        assertThat(ProRatingCalculator.roundHalfUpToHalf(new BigDecimal("5.25"))).isEqualByComparingTo("5.5");
        assertThat(ProRatingCalculator.roundHalfUpToHalf(new BigDecimal("5.2499"))).isEqualByComparingTo("5.0");
        assertThat(ProRatingCalculator.roundHalfUpToHalf(new BigDecimal("5.75"))).isEqualByComparingTo("6.0");
        assertThat(ProRatingCalculator.roundHalfUpToHalf(new BigDecimal("0"))).isEqualByComparingTo("0.0");
    }
}
