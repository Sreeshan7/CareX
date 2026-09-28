package com.carex.leave.assistant.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic date policy (implementation.md §18.4). Today = Monday 2026-09-28. */
class DateExpressionResolverTest {
    static final LocalDate TODAY = LocalDate.parse("2026-09-28");

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "next monday, 2026-10-05",
            "next Friday, 2026-10-09",
            "this friday, 2026-10-02",
            "coming wednesday, 2026-09-30",
            "tomorrow, 2026-09-29",
            "day after tomorrow, 2026-09-30",
            "today, 2026-09-28",
            "12 october, 2026-10-12",
            "Oct 12, 2026-10-12",
            "3rd of january, 2027-01-03",
            "2026-11-02, 2026-11-02",
            "on friday, 2026-10-02",
            "the 15th, 2026-10-15",
    })
    void resolves(String expr, String expected) {
        var r = DateExpressionResolver.resolve(expr, TODAY);
        assertThat(r.isDate()).isTrue();
        assertThat(r.date()).isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({"next week", "this weekend", "soon", "next month", "end of month", "after diwali"})
    void vagueExpressionsAreNeverGuessed(String expr) {
        var r = DateExpressionResolver.resolve(expr, TODAY);
        assertThat(r.kind()).isEqualTo(DateExpressionResolver.Kind.VAGUE);
        assertThat(r.date()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"whenever", "a wedding"})
    void unknownIsUnknown(String expr) {
        assertThat(DateExpressionResolver.resolve(expr, TODAY).kind()).isEqualTo(DateExpressionResolver.Kind.UNKNOWN);
    }
}
