package com.carex.leave.assistant.extract;

import com.carex.leave.assistant.core.Intent;

import java.time.LocalDate;

/**
 * What an extractor believes the user said. Dates carry the verbatim expression so the backend's deterministic
 * resolver (not the model) decides the actual date. {@code modelDate} is only a fallback and is marked INFERRED.
 */
public record RawExtraction(Intent intent, String leaveType, boolean leaveTypeExplicit, DateMention start,
                            DateMention end, Integer durationDays, String reason, Long requestId, String comment,
                            String vaguePeriod, String extractor) {

    public record DateMention(String expression, LocalDate modelDate) {
        public boolean isEmpty() { return (expression == null || expression.isBlank()) && modelDate == null; }
    }

    public static RawExtraction unknown(String extractor) {
        return new RawExtraction(Intent.UNKNOWN, null, false, null, null, null, null, null, null, null, extractor);
    }

    public boolean hasSlots() {
        return leaveType != null || (start != null && !start.isEmpty()) || (end != null && !end.isEmpty())
                || durationDays != null || requestId != null || vaguePeriod != null;
    }
}
