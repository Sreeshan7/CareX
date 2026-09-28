package com.carex.leave.assistant.extract;

import com.carex.leave.assistant.core.DateExpressionResolver;
import com.carex.leave.assistant.core.Intent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback extractor (implementation.md §18.2): works with no AI provider at all.
 * English-first, with a handful of Hindi/Tamil keywords. Only extracts what is literally present.
 */
@Component
public class RuleBasedIntentExtractor {
    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", 1), Map.entry("a", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9),
            Map.entry("ten", 10));
    private static final Pattern DURATION = Pattern.compile(
            "\\b(\\d{1,2}|one|a|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:working\\s+|business\\s+)?days?\\b");
    private static final Pattern REQUEST_ID = Pattern.compile("#?\\b(\\d{4,7})\\b");
    private static final Pattern RANGE = Pattern.compile("\\bfrom\\s+(.+?)\\s+(?:to|till|until|through|-)\\s+(.+?)(?:\\s+for\\b|[,.]|$)");
    private static final Pattern ISO_RANGE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(?:to|till|until|\\.\\.|–|-)\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern REASON = Pattern.compile("\\b(?:for|because of|due to)\\s+(?:a\\s+|an\\s+|my\\s+|the\\s+|our\\s+)?([\\p{L} ]{3,60}?)(?=\\s+(?:from|on|next|this|starting|till|until|to|for)\\b|[,.!?]|$)");
    private static final Pattern REJECT_COMMENT = Pattern.compile("\\b(?:because of|because|due to|as|since|reason:?)\\s+(.{3,})$");

    public RawExtraction extract(String text) {
        String raw = text == null ? "" : text.trim();
        String t = raw.toLowerCase(Locale.ROOT);
        Intent intent = intent(t);
        Long requestId = null;
        Matcher id = REQUEST_ID.matcher(t);
        boolean idIntent = intent == Intent.APPROVE_REQUEST || intent == Intent.REJECT_REQUEST
                || intent == Intent.CANCEL_LEAVE || intent == Intent.QUERY_REQUEST_STATUS;
        // a bare "#1001" / "request 1001" is an answer to a "which request?" clarification
        boolean bareId = intent == Intent.UNKNOWN && t.matches("\\s*(request\\s*)?#?\\d{4,7}\\s*[.!]?\\s*");
        if ((idIntent || bareId) && id.find()) {
            requestId = Long.parseLong(id.group(1));
        }
        String comment = null;
        if (intent == Intent.REJECT_REQUEST) {
            Matcher c = REJECT_COMMENT.matcher(raw);
            if (c.find()) comment = c.group(1).trim();
        }

        // ---- dates
        List<String> mentions = new ArrayList<>();
        Matcher iso = ISO_RANGE.matcher(t);
        Matcher range = RANGE.matcher(t);
        if (iso.find()) {
            mentions.add(iso.group(1));
            mentions.add(iso.group(2));
        } else if (range.find() && isDate(range.group(1)) && isDate(range.group(2))) {
            mentions.add(range.group(1));
            mentions.add(range.group(2));
        } else {
            mentions.addAll(findDateExpressions(t));
        }
        String vague = null;
        Matcher v = DateExpressionResolver.VAGUE.matcher(t);
        if (mentions.isEmpty() && v.find()) vague = v.group(1);

        Integer duration = null;
        Matcher d = DURATION.matcher(t);
        if (d.find()) {
            String n = d.group(1);
            duration = n.chars().allMatch(Character::isDigit) ? Integer.parseInt(n) : NUMBER_WORDS.get(n);
        }
        String type = LeaveTypeKeywords.find(t).orElse(null);
        String reason = null;
        if (intent == Intent.APPLY_LEAVE || intent == Intent.UNKNOWN) {
            Matcher r = REASON.matcher(raw);
            while (r.find()) {
                String cand = r.group(1).trim();
                String lc = cand.toLowerCase(Locale.ROOT);
                if (DURATION.matcher(lc).find() || lc.matches(".*\\b(day|days|week|leave|me)\\b.*") || isDate(lc)) continue;
                reason = cand;
                break;
            }
        }
        if (intent == Intent.UNKNOWN && (!mentions.isEmpty() || vague != null || duration != null || type != null)
                && t.matches("(?s).*\\b(leave|off|holiday|vacation|break|apply|take)\\b.*")) {
            intent = Intent.APPLY_LEAVE;
        }
        RawExtraction.DateMention start = mentions.isEmpty() ? null : new RawExtraction.DateMention(mentions.get(0), null);
        RawExtraction.DateMention end = mentions.size() > 1 ? new RawExtraction.DateMention(mentions.get(1), null) : null;
        return new RawExtraction(intent, type, type != null, start, end, duration, reason, requestId, comment, vague, "RULES");
    }

    private static boolean isDate(String s) {
        return DateExpressionResolver.resolve(s, java.time.LocalDate.of(2026, 1, 1)).isDate();
    }

    static List<String> findDateExpressions(String t) {
        List<int[]> spans = new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (Pattern p : List.of(DateExpressionResolver.ISO, DateExpressionResolver.DAY_MONTH, DateExpressionResolver.MONTH_DAY,
                DateExpressionResolver.SIMPLE, DateExpressionResolver.RELATIVE_WEEKDAY, DateExpressionResolver.DAY_ONLY,
                DateExpressionResolver.BARE_WEEKDAY)) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                int s = m.start(), e = m.end();
                boolean overlaps = spans.stream().anyMatch(sp -> s < sp[1] && e > sp[0]);
                if (!overlaps) {
                    spans.add(new int[]{s, e});
                }
            }
        }
        spans.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] sp : spans) out.add(t.substring(sp[0], sp[1]).trim());
        return out;
    }

    static Intent intent(String t) {
        if (t.matches("(?s).*\\b(approve|approval for)\\b.*\\d{4,}.*") || t.matches("(?s)^\\s*approve\\b.*")) return Intent.APPROVE_REQUEST;
        if (t.matches("(?s).*\\breject\\b.*") || t.matches("(?s).*\\bdecline\\b.*\\d{4,}.*")) return Intent.REJECT_REQUEST;
        if (t.matches("(?s).*\\b(cancel|withdraw)\\b.*") || t.contains("रद्द") || t.contains("ரத்து")) return Intent.CANCEL_LEAVE;
        if (t.matches("(?s).*\\b(balance|leaves? left|days? left|remaining|how many (days|leaves))\\b.*")
                || t.contains("बैलेंस") || t.contains("शेष") || t.contains("இருப்பு") || t.contains("மீதி")) return Intent.QUERY_BALANCE;
        if (t.matches("(?s).*\\bescalat.*")) return Intent.QUERY_ESCALATIONS;
        if (t.matches("(?s).*\\b(conflicts?|clash|flagged|flags?)\\b.*")) return Intent.QUERY_CONFLICTS;
        if (t.matches("(?s).*\\b(pending approvals?|approvals?|to approve|awaiting (my|me)|my queue)\\b.*")) return Intent.QUERY_PENDING_APPROVALS;
        if (t.matches("(?s).*\\b(who('s| is)? (off|out|on leave|away)|team (leave|calendar|absence)|my team)\\b.*")) return Intent.QUERY_TEAM_LEAVE;
        if (t.matches("(?s).*\\b(overview|summary|stats|statistics|report)\\b.*")) return Intent.QUERY_LEAVE_OVERVIEW;
        if (t.matches("(?s).*\\b(holidays?|public holiday)\\b.*") && !t.matches("(?s).*\\b(apply|take|need)\\b.*")) return Intent.QUERY_HOLIDAYS;
        if (t.matches("(?s).*\\b(status of|status)\\b.*\\d{4,}.*") || t.matches("(?s).*\\brequest\\s*#?\\d{4,}.*")) return Intent.QUERY_REQUEST_STATUS;
        if (t.matches("(?s).*\\b(my requests?|my leaves|my applications?|leave history|status)\\b.*")) return Intent.QUERY_MY_REQUESTS;
        if (t.matches("(?s).*\\b(policy|rules|how does|how do i|approval process|help)\\b.*")) return Intent.POLICY_HELP;
        if (t.matches("(?s)^\\s*(hi|hello|hey|namaste|vanakkam|good (morning|afternoon|evening))\\b.*") || t.startsWith("नमस्ते") || t.startsWith("வணக்கம்")) return Intent.GREETING;
        if (t.matches("(?s).*\\b(leave|day off|days off|time off|apply|vacation)\\b.*") || t.contains("छुट्टी") || t.contains("अवकाश")
                || t.contains("விடுப்பு") || t.contains("லீவ்")) return Intent.APPLY_LEAVE;
        return Intent.UNKNOWN;
    }
}
