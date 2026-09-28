package com.carex.leave.leave.balance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * implementation.md §9.3 — month-based pro-rating with a 15th-of-month cutoff, rounded to the nearest 0.5 day,
 * ties rounded up (favouring the employee). BigDecimal only.
 */
public final class ProRatingCalculator {
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private ProRatingCalculator() {}

    /** Empty when the user joins after the end of {@code year} (no balance row for that year). */
    public static Optional<Result> entitlement(BigDecimal annual, boolean prorated, LocalDate joiningDate, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        if (joiningDate.isAfter(yearEnd)) {
            return Optional.empty();
        }
        if (!prorated || joiningDate.isBefore(yearStart)) {
            BigDecimal full = annual.setScale(1, RoundingMode.UNNECESSARY);
            String why = !prorated ? "Not pro-rated: full entitlement " + full
                    : "Joined before " + year + ": full entitlement " + full;
            return Optional.of(new Result(full, 12, why));
        }
        int months = 12 - joiningDate.getMonthValue() + 1;
        if (joiningDate.getDayOfMonth() > 15) {
            months -= 1;
        }
        BigDecimal raw = annual.multiply(BigDecimal.valueOf(months)).divide(TWELVE, 4, RoundingMode.HALF_UP);
        BigDecimal rounded = roundHalfUpToHalf(raw);
        String why = "Joined " + joiningDate + "; " + months + "/12 months × " + annual.stripTrailingZeros().toPlainString()
                + " = " + raw.setScale(2, RoundingMode.HALF_UP).toPlainString() + " → " + rounded.toPlainString();
        return Optional.of(new Result(rounded, months, why));
    }

    /** floor(x × 2 + 0.5) / 2 */
    public static BigDecimal roundHalfUpToHalf(BigDecimal x) {
        return x.multiply(TWO).add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR)
                .divide(TWO, 1, RoundingMode.UNNECESSARY);
    }

    public record Result(BigDecimal entitled, int monthsEligible, String basis) {}
}
