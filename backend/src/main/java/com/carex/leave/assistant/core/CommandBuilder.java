package com.carex.leave.assistant.core;

import com.carex.leave.assistant.core.CanonicalCommand.Ambiguity;
import com.carex.leave.assistant.core.CanonicalCommand.Slot;
import com.carex.leave.assistant.extract.LeaveTypeKeywords;
import com.carex.leave.assistant.extract.RawExtraction;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an extraction (+ the previous draft, for multi-turn slot filling) into a canonical command with slot
 * provenance. Dates are ALWAYS resolved here by {@link DateExpressionResolver}, never taken from the model as-is.
 */
@Component
public class CommandBuilder {
    private final WorkingDayCalculator workingDays;

    public CommandBuilder(WorkingDayCalculator workingDays) {
        this.workingDays = workingDays;
    }

    public CanonicalCommand build(RawExtraction raw, CanonicalCommand draft, String text, String language, LocalDate today) {
        boolean continuing = draft != null && draft.intent() != null && draft.intent().isMutation()
                && (raw.intent() == Intent.UNKNOWN || raw.intent() == draft.intent());
        Intent intent = continuing ? draft.intent() : raw.intent();
        Map<String, Slot> slots = new LinkedHashMap<>();
        if (continuing && draft.slots() != null) {
            draft.slots().forEach((k, v) -> { if (v != null && v.value() != null) slots.put(k, v); });
        }
        List<Ambiguity> ambiguities = new ArrayList<>();
        String awaiting = continuing ? draft.awaiting() : null;

        // free-text answer to "Reason for rejection?"
        if (continuing && "comment".equals(awaiting) && raw.comment() == null && raw.intent() == Intent.UNKNOWN
                && text != null && text.trim().length() >= 3) {
            slots.put("comment", Slot.explicit(text.trim()));
        }

        // leave type: EXPLICIT only if literally named by the user
        if (raw.leaveType() != null) {
            boolean named = LeaveTypeKeywords.mentions(text, raw.leaveType()) || raw.leaveTypeExplicit() && "RULES".equals(raw.extractor());
            slots.put("leaveType", named ? Slot.explicit(raw.leaveType()) : Slot.inferred(raw.leaveType(), null));
        }

        // dates
        RawExtraction.DateMention start = raw.start();
        RawExtraction.DateMention end = raw.end();
        if (continuing && "endDate".equals(awaiting) && start != null && end == null) {
            end = start;           // "until Friday" answered to "until when?"
            start = null;
        }
        Slot startSlot = dateSlot(start, today, ambiguities, "startDate");
        Slot endSlot = dateSlot(end, today, ambiguities, "endDate");
        if (startSlot != null) {
            slots.put("startDate", startSlot);
            if (endSlot == null) {
                Slot oldEnd = slots.get("endDate");
                if (oldEnd != null && oldEnd.value() != null && oldEnd.value().toString().compareTo(startSlot.value().toString()) < 0) {
                    slots.remove("endDate");
                }
                if (oldEnd != null && "RESOLVED".equals(oldEnd.source()) && oldEnd.expression() != null
                        && oldEnd.expression().endsWith("working days")) {
                    slots.remove("endDate"); // recompute from duration against the new start
                }
            }
        }
        if (endSlot != null) slots.put("endDate", endSlot);
        if (raw.durationDays() != null) {
            slots.put("durationWorkingDays", Slot.explicit(raw.durationDays()));
            if (endSlot == null && slots.get("endDate") != null && "RESOLVED".equals(slots.get("endDate").source())) {
                slots.remove("endDate");
            }
        }
        if (raw.vaguePeriod() != null && startSlot == null) {
            DateExpressionResolver.Resolution r = DateExpressionResolver.resolve(raw.vaguePeriod(), today);
            String reason = "vague:" + raw.vaguePeriod()
                    + (r.periodStart() != null ? ":" + r.periodStart() + ".." + r.periodEnd() : "");
            ambiguities.add(new Ambiguity("startDate", reason));
            slots.remove("startDate");
            slots.remove("endDate");
        }
        // derive end from duration (deterministic working-day arithmetic)
        Slot s = slots.get("startDate");
        Slot dur = slots.get("durationWorkingDays");
        if (s != null && s.trusted() && slots.get("endDate") == null && dur != null) {
            int n = ((Number) (dur.value() instanceof Number ? dur.value() : Integer.parseInt(dur.value().toString()))).intValue();
            if (n >= 1 && n <= 30) {
                LocalDate from = workingDays.nextWorkingDayOnOrAfter(LocalDate.parse(s.value().toString()));
                slots.put("endDate", Slot.resolved(workingDays.addWorkingDays(from, n).toString(), n + " working days"));
            }
        }
        if (raw.reason() != null && !raw.reason().isBlank()) slots.put("reason", Slot.explicit(raw.reason().trim()));
        if (raw.requestId() != null) slots.put("requestId", Slot.explicit(raw.requestId()));
        if (raw.comment() != null && !raw.comment().isBlank()) slots.put("comment", Slot.explicit(raw.comment().trim()));

        return new CanonicalCommand(CanonicalCommand.VERSION, intent, language, slots, new ArrayList<>(), ambiguities,
                null, null);
    }

    private static Slot dateSlot(RawExtraction.DateMention m, LocalDate today, List<Ambiguity> ambiguities, String name) {
        if (m == null || m.isEmpty()) return null;
        if (m.expression() != null) {
            DateExpressionResolver.Resolution r = DateExpressionResolver.resolve(m.expression(), today);
            switch (r.kind()) {
                case ABSOLUTE -> { return new Slot(r.date().toString(), "EXPLICIT", m.expression()); }
                case RELATIVE -> { return Slot.resolved(r.date().toString(), m.expression()); }
                case VAGUE -> {
                    ambiguities.add(new Ambiguity(name, "vague:" + m.expression()
                            + (r.periodStart() != null ? ":" + r.periodStart() + ".." + r.periodEnd() : "")));
                    return null;
                }
                default -> { /* fall through to model value */ }
            }
        }
        // the model produced a date we cannot verify from the user's words → never trusted
        return m.modelDate() == null ? null : Slot.inferred(m.modelDate().toString(), m.expression());
    }
}
