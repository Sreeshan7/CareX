package com.carex.leave.conflict;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictResultTest {
    private static final LocalDate D5 = LocalDate.parse("2026-10-05");
    private static final LocalDate D6 = LocalDate.parse("2026-10-06");
    private static final LocalDate D7 = LocalDate.parse("2026-10-07");
    private static final LocalDate D8 = LocalDate.parse("2026-10-08");
    private final List<ConflictResult.Member> six = LongStream.rangeClosed(1, 6)
            .mapToObj(i -> new ConflictResult.Member(i, LocalDate.parse("2020-01-01"))).toList();

    @Test
    void blueprintExampleArjunAndBalaIsFlagged() {
        // Arjun (1) off 5–7 Oct, Bala (2) off 6–8 Oct → 2/6 = 33% > 30% with ≥2 absent on 6 & 7 Oct
        var r = ConflictResult.compute(List.of(D6, D7, D8),
                List.of(new ConflictResult.Absence(10L, 1L, D5, D7), new ConflictResult.Absence(11L, 2L, D6, D8)),
                six, new BigDecimal("30"), 2);
        assertThat(r.flagged()).isTrue();
        assertThat(r.peakDate()).isEqualTo(D6);
        assertThat(r.peakAbsent()).isEqualTo(2);
        assertThat(r.teamSize()).isEqualTo(6);
        assertThat(r.overlappingRequestIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void singleAbsenceIsNotFlaggedBecauseOfMinAbsent() {
        var r = ConflictResult.compute(List.of(D5), List.of(new ConflictResult.Absence(10L, 1L, D5, D7)),
                List.of(six.get(0), six.get(1)), new BigDecimal("30"), 2);
        assertThat(r.flagged()).isFalse(); // 1/2 = 50% but only 1 absent
    }

    @Test
    void exactlyAtThresholdIsNotFlagged() {
        // 3 of 10 = 30% is not > 30%
        List<ConflictResult.Member> ten = LongStream.rangeClosed(1, 10)
                .mapToObj(i -> new ConflictResult.Member(i, LocalDate.parse("2020-01-01"))).toList();
        var r = ConflictResult.compute(List.of(D5), List.of(
                new ConflictResult.Absence(1L, 1L, D5, D5), new ConflictResult.Absence(2L, 2L, D5, D5),
                new ConflictResult.Absence(3L, 3L, D5, D5)), ten, new BigDecimal("30"), 2);
        assertThat(r.flagged()).isFalse();
    }

    @Test
    void futureJoinersDoNotCountTowardsTeamSize() {
        List<ConflictResult.Member> members = List.of(new ConflictResult.Member(1L, LocalDate.parse("2020-01-01")),
                new ConflictResult.Member(2L, LocalDate.parse("2020-01-01")),
                new ConflictResult.Member(3L, LocalDate.parse("2027-01-01")));
        var r = ConflictResult.compute(List.of(D5), List.of(), members, new BigDecimal("30"), 2);
        assertThat(r.teamSize()).isEqualTo(2);
    }
}
