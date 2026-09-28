package com.carex.leave.assistant.extract;

import com.carex.leave.assistant.core.Intent;
import com.carex.leave.assistant.provider.AiPorts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM-based intent + slot extraction (implementation.md §18.6). The model sees ONLY the user's message and
 * static context (date, role, allowed intents). It never sees database data and has no tools. Its output is
 * parsed into a strict shape: unknown intents → UNKNOWN, invalid dates/types/ids are dropped.
 */
@Component
public class LlmIntentExtractor {
    private static final Logger log = LoggerFactory.getLogger(LlmIntentExtractor.class);
    private static final Set<String> TYPES = Set.of("ANNUAL", "CASUAL", "SICK");

    private final AiPorts.ChatLlm llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String template;

    public LlmIntentExtractor(AiPorts.ChatLlm llm) throws IOException {
        this.llm = llm;
        this.template = new ClassPathResource("prompts/intent-system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    public boolean available() {
        return llm.available();
    }

    public RawExtraction extract(String text, LocalDate today, String role, Collection<Intent> allowed, String awaitingSlot) {
        String system = template
                .replace("{{today}}", today.toString())
                .replace("{{weekday}}", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .replace("{{role}}", role)
                .replace("{{intents}}", allowed.stream().map(Enum::name).collect(Collectors.joining(", ")))
                .replace("{{awaiting}}", awaitingSlot == null ? "There is no pending question."
                        : "The assistant just asked the user for: " + awaitingSlot + ".");
        String user = "<user_message>" + (text.length() > 500 ? text.substring(0, 500) : text) + "</user_message>";
        JsonNode json = null;
        for (int attempt = 0; attempt < 2 && json == null; attempt++) {
            String out = llm.complete(system, attempt == 0 ? user : user + "\nReturn only valid JSON.", 400);
            json = parse(out);
        }
        if (json == null) {
            throw new AiPorts.AiUnavailable("LLM returned no parseable JSON");
        }
        return map(json);
    }

    JsonNode parse(String out) {
        if (out == null) return null;
        int s = out.indexOf('{');
        int e = out.lastIndexOf('}');
        if (s < 0 || e <= s) return null;
        try {
            return mapper.readTree(out.substring(s, e + 1));
        } catch (Exception ex) {
            log.debug("LLM JSON parse failed");
            return null;
        }
    }

    static RawExtraction map(JsonNode j) {
        Intent intent = Intent.parse(textOrNull(j, "intent"));
        String type = textOrNull(j, "leaveType");
        type = type != null && TYPES.contains(type.toUpperCase(Locale.ROOT)) ? type.toUpperCase(Locale.ROOT) : null;
        boolean explicit = j.path("leaveTypeExplicit").asBoolean(false);
        Integer duration = j.hasNonNull("durationDays") && j.get("durationDays").canConvertToInt()
                && j.get("durationDays").asInt() > 0 && j.get("durationDays").asInt() <= 60 ? j.get("durationDays").asInt() : null;
        Long requestId = j.hasNonNull("requestId") && j.get("requestId").canConvertToLong() && j.get("requestId").asLong() > 0
                ? j.get("requestId").asLong() : null;
        return new RawExtraction(intent, type, explicit, mention(j.get("startDate")), mention(j.get("endDate")), duration,
                clip(textOrNull(j, "reason"), 200), requestId, clip(textOrNull(j, "comment"), 500),
                clip(textOrNull(j, "vaguePeriod"), 40), "LLM");
    }

    private static RawExtraction.DateMention mention(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String expr = textOrNull(n, "expression");
        LocalDate value = null;
        String v = textOrNull(n, "value");
        if (v != null) {
            try {
                value = LocalDate.parse(v);
            } catch (RuntimeException ignored) {
                // invalid model date → dropped
            }
        }
        RawExtraction.DateMention m = new RawExtraction.DateMention(clip(expr, 60), value);
        return m.isEmpty() ? null : m;
    }

    private static String textOrNull(JsonNode j, String f) {
        JsonNode n = j == null ? null : j.get(f);
        if (n == null || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
