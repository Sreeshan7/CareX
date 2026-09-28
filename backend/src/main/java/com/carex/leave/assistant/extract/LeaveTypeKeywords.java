package com.carex.leave.assistant.extract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A leave type counts as EXPLICIT only if the user actually named it (in English, Hindi or Tamil).
 * The type is never inferred from the reason ("wedding" does NOT imply CASUAL) — implementation.md §18.5.
 */
public final class LeaveTypeKeywords {
    private static final Map<String, List<String>> WORDS = new LinkedHashMap<>();

    static {
        WORDS.put("SICK", List.of("sick", "medical", "बीमारी", "बीमार", "मेडिकल", "चिकित्सा", "சுகவீன", "நோய்", "மருத்துவ"));
        WORDS.put("CASUAL", List.of("casual", "आकस्मिक", "कैजुअल", "कैज़ुअल", "சாதாரண", "கேஷுவல்", "தற்செயல்"));
        WORDS.put("ANNUAL", List.of("annual", "earned", "privilege", "vacation", "वार्षिक", "अर्जित", "सालाना", "ஆண்டு", "வருடாந்திர", "ஈட்டிய"));
    }

    private LeaveTypeKeywords() {}

    public static Optional<String> find(String text) {
        if (text == null) return Optional.empty();
        String t = text.toLowerCase(Locale.ROOT);
        for (var e : WORDS.entrySet()) {
            for (String w : e.getValue()) {
                if (w.chars().allMatch(c -> c < 128) ? t.matches("(?s).*\\b" + w + "\\b.*") : t.contains(w)) {
                    return Optional.of(e.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public static boolean mentions(String text, String code) {
        return find(text).map(code::equals).orElse(false);
    }
}
