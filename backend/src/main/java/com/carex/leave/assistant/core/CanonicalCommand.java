package com.carex.leave.assistant.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The canonical structured command (implementation.md §18.3). Slots carry provenance:
 * EXPLICIT (stated literally), RESOLVED (relative expression resolved by backend rules), INFERRED (model guess —
 * never accepted for critical slots). The client echoes it back on the next turn; the server re-validates it
 * every time, so a tampered draft can never do more than a fresh message could.
 */
public record CanonicalCommand(String schemaVersion, Intent intent, String language, Map<String, Slot> slots,
                               List<String> missingSlots, List<Ambiguity> ambiguities, String status,
                               String awaiting) {

    public static final String VERSION = "1.0";

    public record Slot(Object value, String source, String expression) {
        public static Slot explicit(Object v) { return new Slot(v, "EXPLICIT", null); }
        public static Slot resolved(Object v, String expr) { return new Slot(v, "RESOLVED", expr); }
        public static Slot inferred(Object v, String expr) { return new Slot(v, "INFERRED", expr); }
        public boolean trusted() { return "EXPLICIT".equals(source) || "RESOLVED".equals(source); }
    }

    public record Ambiguity(String slot, String reason) {}

    public static CanonicalCommand empty(Intent intent, String language) {
        return new CanonicalCommand(VERSION, intent, language, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(),
                null, null);
    }

    public CanonicalCommand withStatus(String s, String awaitingSlot, List<String> missing) {
        return new CanonicalCommand(VERSION, intent, language, slots, missing, ambiguities, s, awaitingSlot);
    }

    public Slot slot(String name) {
        return slots == null ? null : slots.get(name);
    }

    public String str(String name) {
        Slot s = slot(name);
        return s == null || s.value() == null ? null : String.valueOf(s.value());
    }
}
