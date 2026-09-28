package com.carex.leave.assistant.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic resolution of date expressions (implementation.md §18.4). The backend — never the model —
 * decides what "next Monday" means:
 * <ul>
 *   <li>"next &lt;weekday&gt;" = that weekday in the following calendar week (Mon–Sun). Today Mon 28 Sep 2026 →
 *       "next Monday" = Mon 5 Oct, "next Friday" = Fri 9 Oct.</li>
 *   <li>"this &lt;weekday&gt;" / bare weekday = the nearest occurrence on/after today in the current week, else next.</li>
 *   <li>"12 Oct", "Oct 12", "12th" = that date this year/month, or the next one if already past.</li>
 *   <li>"next week", "weekend", "soon", "next month" … = VAGUE → the assistant must ask.</li>
 * </ul>
 */
public final class DateExpressionResolver {
    private static final String WEEKDAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun";
    private static final String MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec";

    public static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    public static final Pattern DAY_MONTH = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(" + MONTHS + ")\\b\\.?(?:\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    public static final Pattern MONTH_DAY = Pattern.compile("\\b(" + MONTHS + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    public static final Pattern DAY_ONLY = Pattern.compile("\\b(?:on\\s+)?the\\s+(\\d{1,2})(?:st|nd|rd|th)\\b", Pattern.CASE_INSENSITIVE);
    public static final Pattern RELATIVE_WEEKDAY = Pattern.compile("\\b(next|this|coming)\\s+(" + WEEKDAYS + ")\\b", Pattern.CASE_INSENSITIVE);
    public static final Pattern BARE_WEEKDAY = Pattern.compile("\\b(?:on\\s+)?(" + WEEKDAYS + ")\\b", Pattern.CASE_INSENSITIVE);
    public static final Pattern SIMPLE = Pattern.compile("\\b(day after tomorrow|tomorrow|today)\\b", Pattern.CASE_INSENSITIVE);
    public static final Pattern VAGUE = Pattern.compile("\\b(next week|this week|coming week|the weekend|this weekend|next weekend|weekend|next month|this month|end of (?:the )?month|soon|sometime|later|after diwali|after pongal)\\b", Pattern.CASE_INSENSITIVE);

    public enum Kind { ABSOLUTE, RELATIVE, VAGUE, UNKNOWN }

    public record Resolution(Kind kind, LocalDate date, LocalDate periodStart, LocalDate periodEnd) {
        public boolean isDate() { return kind == Kind.ABSOLUTE || kind == Kind.RELATIVE; }
    }

    private DateExpressionResolver() {}

    public static Resolution resolve(String expression, LocalDate today) {
        if (expression == null || expression.isBlank()) return new Resolution(Kind.UNKNOWN, null, null, null);
        String e = expression.trim().toLowerCase(Locale.ROOT);
        Matcher m;
        if ((m = ISO.matcher(e)).find()) {
            try {
                return abs(LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
            } catch (RuntimeException ex) {
                return new Resolution(Kind.UNKNOWN, null, null, null);
            }
        }
        if ((m = VAGUE.matcher(e)).find()) {
            String v = m.group(1);
            if (v.contains("week") && !v.contains("weekend")) {
                LocalDate monday = v.startsWith("this") ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        : today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                return new Resolution(Kind.VAGUE, null, monday, monday.plusDays(4));
            }
            return new Resolution(Kind.VAGUE, null, null, null);
        }
        if ((m = SIMPLE.matcher(e)).find()) {
            String s = m.group(1);
            return rel(s.equals("today") ? today : s.equals("tomorrow") ? today.plusDays(1) : today.plusDays(2));
        }
        if ((m = DAY_MONTH.matcher(e)).find()) {
            return dayMonth(Integer.parseInt(m.group(1)), month(m.group(2)), m.group(3), today);
        }
        if ((m = MONTH_DAY.matcher(e)).find()) {
            return dayMonth(Integer.parseInt(m.group(2)), month(m.group(1)), m.group(3), today);
        }
        if ((m = RELATIVE_WEEKDAY.matcher(e)).find()) {
            DayOfWeek dow = weekday(m.group(2));
            if (m.group(1).equals("next")) {
                LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                return rel(nextMonday.with(TemporalAdjusters.nextOrSame(dow)));
            }
            return rel(today.with(TemporalAdjusters.nextOrSame(dow)));
        }
        if ((m = DAY_ONLY.matcher(e)).find()) {
            int d = Integer.parseInt(m.group(1));
            try {
                LocalDate candidate = today.withDayOfMonth(d);
                if (candidate.isBefore(today)) candidate = today.plusMonths(1).withDayOfMonth(d);
                return rel(candidate);
            } catch (RuntimeException ex) {
                return new Resolution(Kind.UNKNOWN, null, null, null);
            }
        }
        if ((m = BARE_WEEKDAY.matcher(e)).find()) {
            return rel(today.with(TemporalAdjusters.nextOrSame(weekday(m.group(1)))));
        }
        return new Resolution(Kind.UNKNOWN, null, null, null);
    }

    private static Resolution dayMonth(int day, Month month, String year, LocalDate today) {
        try {
            if (year != null) return abs(LocalDate.of(Integer.parseInt(year), month, day));
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            if (d.isBefore(today)) d = d.plusYears(1);
            return abs(d);
        } catch (RuntimeException ex) {
            return new Resolution(Kind.UNKNOWN, null, null, null);
        }
    }

    private static Resolution abs(LocalDate d) { return new Resolution(Kind.ABSOLUTE, d, null, null); }
    private static Resolution rel(LocalDate d) { return new Resolution(Kind.RELATIVE, d, null, null); }

    static DayOfWeek weekday(String s) {
        String k = s.toLowerCase(Locale.ROOT).substring(0, 3);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT).startsWith(k)) return d;
        }
        throw new IllegalArgumentException(s);
    }

    static Month month(String s) {
        String k = s.toLowerCase(Locale.ROOT).substring(0, 3);
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT).startsWith(k)) return m;
        }
        throw new IllegalArgumentException(s);
    }
}
