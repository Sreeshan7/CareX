package com.carex.leave.assistant.core;

import com.carex.leave.assistant.api.AssistantDtos.Card;
import com.carex.leave.assistant.api.AssistantDtos.Clarification;
import com.carex.leave.assistant.api.AssistantDtos.Option;
import com.carex.leave.assistant.api.AssistantDtos.ProposedAction;
import com.carex.leave.assistant.api.AssistantDtos.Reply;
import com.carex.leave.assistant.core.CanonicalCommand.Slot;
import com.carex.leave.assistant.extract.LlmIntentExtractor;
import com.carex.leave.assistant.extract.RawExtraction;
import com.carex.leave.assistant.extract.RuleBasedIntentExtractor;
import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.common.web.ClientChannel;
import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.query.LeavePreviewService;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views;
import com.carex.leave.telemetry.AssistantTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The assistant pipeline (implementation.md §18.1): extract → build canonical command → validate (role policy,
 * required slots, ambiguity) → answer (read-only) / clarify / PROPOSE → translate.
 *
 * <p>HARD BOUNDARY: this class and everything in {@code ..assistant..} can only READ. There is no code path from
 * here to the workflow write service (enforced by an ArchUnit test). A proposal becomes a real request only when
 * the human clicks Confirm in the UI, which calls the normal REST endpoint with full authN/authZ, balance,
 * conflict, state-machine and audit processing.</p>
 */
@Service
public class AssistantOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);
    /** Short structured answers to clarification questions are parsed deterministically (no LLM round trip). */
    private static final Pattern STRUCTURED = Pattern.compile(
            "(?i)\\s*(from\\s+)?\\d{4}-\\d{2}-\\d{2}(\\s*(to|till|until|\\.\\.)\\s*\\d{4}-\\d{2}-\\d{2})?\\s*|\\s*#?\\d{4,7}\\s*|\\s*\\d{1,2}\\s+(working\\s+)?days?\\s*|\\s*(casual|annual|sick)\\s*");

    private final RuleBasedIntentExtractor rules;
    private final LlmIntentExtractor llm;
    private final CommandBuilder builder;
    private final AssistantAnswers answers;
    private final LeaveQueryService queries;
    private final LeavePreviewService preview;
    private final BalanceService balances;
    private final AiPorts.Translation translation;
    private final BusinessCalendar calendar;
    private final AssistantTelemetry telemetry;

    public AssistantOrchestrator(RuleBasedIntentExtractor rules, LlmIntentExtractor llm, CommandBuilder builder,
                                 AssistantAnswers answers, LeaveQueryService queries, LeavePreviewService preview,
                                 BalanceService balances, AiPorts.Translation translation, BusinessCalendar calendar,
                                 AssistantTelemetry telemetry) {
        this.rules = rules;
        this.llm = llm;
        this.builder = builder;
        this.answers = answers;
        this.queries = queries;
        this.preview = preview;
        this.balances = balances;
        this.translation = translation;
        this.calendar = calendar;
        this.telemetry = telemetry;
    }

    private record Draft(String text, Clarification clarification, ProposedAction proposal, List<Card> cards,
                         CanonicalCommand command, String outcome) {}

    public Reply handle(CurrentUser me, String text, String language, CanonicalCommand draft) {
        long t0 = System.nanoTime();
        String lang = normalizeLanguage(language);
        LocalDate today = calendar.today();
        List<String> degraded = new ArrayList<>();

        // ---- 1. extraction (LLM → rules fallback)
        RawExtraction raw;
        String awaiting = draft == null ? null : draft.awaiting();
        if (STRUCTURED.matcher(text).matches() || !llm.available()) {
            raw = rules.extract(text);
            if (!llm.available() && !lang.startsWith("en")) degraded.add("AI language understanding unavailable — English keywords only");
        } else {
            try {
                raw = llm.extract(text, today, me.role().name(), IntentPolicy.allowed(me.role()), awaiting);
                if (raw.intent() == Intent.UNKNOWN && !raw.hasSlots()) {
                    RawExtraction r2 = rules.extract(text);
                    if (r2.intent() != Intent.UNKNOWN || r2.hasSlots()) raw = r2;
                }
            } catch (AiPorts.AiUnavailable e) {
                log.warn("assistant: LLM unavailable, using rules ({})", e.getMessage());
                degraded.add("AI understanding unavailable — using keyword matching");
                raw = rules.extract(text);
            }
        }

        // ---- 2. canonical command
        CanonicalCommand cmd = builder.build(raw, draft, text, lang, today);

        // ---- 3. policy + dispatch
        Draft out;
        if (!IntentPolicy.isAllowed(me.role(), cmd.intent())) {
            out = new Draft(policyDenied(cmd.intent()), null, null, List.of(),
                    cmd.withStatus("POLICY_DENIED", null, List.of()), "POLICY_DENIED");
        } else {
            out = switch (cmd.intent()) {
                case APPLY_LEAVE -> apply(me, cmd, today);
                case CANCEL_LEAVE -> cancel(me, cmd);
                case APPROVE_REQUEST, REJECT_REQUEST -> decide(me, cmd);
                case UNKNOWN -> new Draft(AssistantAnswers.help(me.role()), null, null, List.of(),
                        cmd.withStatus("UNSUPPORTED", null, List.of()), "UNSUPPORTED");
                default -> {
                    AssistantAnswers.Answer a = answers.answer(me, cmd);
                    yield new Draft(a.text(), null, null, a.cards(), cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
                }
            };
        }

        // ---- 4. translate (reply text only; numbers/dates are already rendered)
        String english = out.text();
        String reply = english;
        Clarification clar = out.clarification();
        if (!lang.startsWith("en")) {
            if (translation.available()) {
                try {
                    reply = translation.translate(english, "en-IN", lang);
                    if (clar != null) clar = new Clarification(clar.slot(), translation.translate(clar.question(), "en-IN", lang), clar.options());
                } catch (AiPorts.AiUnavailable e) {
                    degraded.add("translation unavailable");
                }
            } else {
                degraded.add("translation unavailable");
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        telemetry.record(me.id(), ClientChannel.current().forRequest().name(), lang, cmd.intent().name(), out.outcome(),
                raw.extractor(), ms, degraded.isEmpty() ? null : "DEGRADED");
        log.info("assistant intent={} outcome={} extractor={} ms={}", cmd.intent(), out.outcome(), raw.extractor(), ms);
        return new Reply(reply, english, degraded.contains("translation unavailable") ? "en-IN" : lang, out.command(),
                clar, out.proposal(), out.cards(), !degraded.isEmpty(), degraded.isEmpty() ? null : String.join("; ", degraded));
    }

    // =========================================================== APPLY_LEAVE
    private Draft apply(CurrentUser me, CanonicalCommand cmd, LocalDate today) {
        Map<String, Slot> s = cmd.slots();
        List<String> missing = new ArrayList<>();
        for (String k : List.of("startDate", "endDate", "leaveType")) {
            if (s.get(k) == null || !s.get(k).trusted()) missing.add(k);
        }
        // (a) vague period ("next week") → ask which days; never guess
        var vague = cmd.ambiguities().stream().filter(a -> a.reason().startsWith("vague:")).findFirst();
        if (vague.isPresent()) {
            String[] parts = vague.get().reason().split(":");
            List<Option> opts = new ArrayList<>();
            if (parts.length > 2 && parts[2].contains("..")) {
                LocalDate a = LocalDate.parse(parts[2].substring(0, 10));
                LocalDate b = LocalDate.parse(parts[2].substring(12));
                opts.add(new Option("Whole week (" + AssistantAnswers.range(a, b) + ")", "from " + a + " to " + b));
                opts.add(new Option("Mon–Wed (" + AssistantAnswers.range(a, a.plusDays(2)) + ")", "from " + a + " to " + a.plusDays(2)));
                opts.add(new Option(AssistantAnswers.date(a) + " only", a.toString()));
            }
            return clarify(cmd, "startDate", "\"" + parts[1] + "\" isn't specific enough — which days exactly?", opts, missing);
        }
        // (b) inferred (unverifiable) dates → confirm explicitly
        for (String k : List.of("startDate", "endDate")) {
            Slot sl = s.get(k);
            if (sl != null && "INFERRED".equals(sl.source())) {
                LocalDate d = LocalDate.parse(sl.value().toString());
                return clarify(cmd, k, "Did you mean " + AssistantAnswers.date(d) + " as the " + (k.equals("startDate") ? "start" : "end")
                        + " date? Please confirm or give the exact date.", List.of(new Option(AssistantAnswers.date(d), d.toString())), missing);
            }
        }
        // (c) dates first, then type (implementation.md §18.5)
        if (missing.contains("startDate")) {
            Slot dur = s.get("durationWorkingDays");
            LocalDate tomorrow = today.plusDays(1);
            LocalDate nextMon = DateExpressionResolver.resolve("next monday", today).date();
            return clarify(cmd, "startDate", "Which date should the leave start" + (dur != null ? " (for " + dur.value() + " working days)" : "") + "?",
                    List.of(new Option("Tomorrow · " + AssistantAnswers.date(tomorrow), tomorrow.toString()),
                            new Option("Next Monday · " + AssistantAnswers.date(nextMon), nextMon.toString())), missing);
        }
        if (missing.contains("endDate")) {
            return clarify(cmd, "endDate", "Starting " + AssistantAnswers.date(LocalDate.parse(s.get("startDate").value().toString()))
                            + " — for how many working days, or until which date?",
                    List.of(new Option("1 day", "1 day"), new Option("2 days", "2 days"), new Option("3 days", "3 days")), missing);
        }
        if (missing.contains("leaveType")) {
            int year = LocalDate.parse(s.get("startDate").value().toString()).getYear();
            List<Option> opts = balances.snapshots(me.id(), year).stream()
                    .map(b -> new Option(b.leaveTypeName() + " · " + AssistantAnswers.days(b.available()) + " left",
                            b.leaveTypeCode().toLowerCase())).toList();
            String inferredHint = s.get("leaveType") != null ? " (I won't guess it from the reason.)" : "";
            return clarify(cmd, "leaveType", "Which type of leave should I use?" + inferredHint, opts, missing);
        }
        // (d) everything explicit/resolved → build a proposal using the SAME read-only preview the form uses
        String type = s.get("leaveType").value().toString();
        LocalDate start = LocalDate.parse(s.get("startDate").value().toString());
        LocalDate end = LocalDate.parse(s.get("endDate").value().toString());
        Views.PreviewResponse pv = preview.preview(me, type, start, end);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leaveTypeCode", type);
        payload.put("startDate", start.toString());
        payload.put("endDate", end.toString());
        payload.put("reason", s.get("reason") == null ? null : s.get("reason").value());
        StringBuilder msg = new StringBuilder("Here's what I understood: ").append(AssistantAnswers.typeName(type))
                .append(" leave, ").append(AssistantAnswers.range(start, end)).append(" — ").append(pv.workingDays())
                .append(" working days.");
        if (pv.availableAfter() != null) msg.append(" After this you will have ").append(AssistantAnswers.days(pv.availableAfter()))
                .append(" days of ").append(AssistantAnswers.typeName(type).toLowerCase()).append(" leave left.");
        if (pv.conflict() != null && pv.conflict().wouldFlag()) msg.append(" Heads-up: high team absence — it will be flagged for your manager, not rejected.");
        if (!pv.errors().isEmpty()) msg.append(" Problem: ").append(pv.errors().get(0).message()).append(". Please adjust before submitting.");
        else msg.append(" Review and confirm to submit — nothing has been submitted yet.");
        ProposedAction p = new ProposedAction("SUBMIT_LEAVE", payload, pv, null, null, true);
        return new Draft(msg.toString(), null, p, List.of(), cmd.withStatus("READY_FOR_CONFIRMATION", null, List.of()), "PROPOSED");
    }

    // =========================================================== CANCEL_LEAVE
    private Draft cancel(CurrentUser me, CanonicalCommand cmd) {
        List<Views.LeaveRequestSummary> cancellable = queries.cancellable(me);
        String id = cmd.str("requestId");
        Views.LeaveRequestSummary target = null;
        if (id != null) {
            target = cancellable.stream().filter(r -> r.id().toString().equals(id)).findFirst().orElse(null);
            if (target == null) {
                return new Draft(AssistantAnswers.notFound(id) + " Only your own pending or future approved leave can be cancelled.",
                        null, null, List.of(), cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
            }
        } else if (cancellable.isEmpty()) {
            return new Draft("You have no leave that can be cancelled.", null, null, List.of(),
                    cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
        } else if (cancellable.size() == 1) {
            target = cancellable.get(0);
        } else {
            List<Option> opts = cancellable.stream().limit(6).map(r -> new Option("#" + r.id() + " " + AssistantAnswers.typeName(r.leaveTypeCode())
                    + " " + AssistantAnswers.range(r.startDate(), r.endDate()), "#" + r.id())).toList();
            return clarify(cmd, "requestId", "Which request do you want to cancel?", opts, List.of("requestId"));
        }
        ProposedAction p = new ProposedAction("CANCEL_LEAVE", Map.of("requestId", target.id()), null, target, null, true);
        return new Draft("Cancel request #" + target.id() + " (" + AssistantAnswers.typeName(target.leaveTypeCode()) + ", "
                + AssistantAnswers.range(target.startDate(), target.endDate()) + ")? Your reserved days return to your balance. "
                + "Confirm to cancel — nothing has changed yet.", null, p, List.of(),
                cmd.withStatus("READY_FOR_CONFIRMATION", null, List.of()), "PROPOSED");
    }

    // =========================================================== APPROVE / REJECT
    private Draft decide(CurrentUser me, CanonicalCommand cmd) {
        boolean approve = cmd.intent() == Intent.APPROVE_REQUEST;
        String id = cmd.str("requestId");
        List<Views.LeaveRequestSummary> queue = me.isHr() ? queries.hrQueue(me) : queries.managerQueue(me);
        if (id == null) {
            if (queue.isEmpty()) {
                return new Draft("Nothing is waiting for your decision.", null, null, List.of(), cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
            }
            List<Option> opts = queue.stream().limit(6).map(r -> new Option("#" + r.id() + " " + r.employee().name() + " "
                    + AssistantAnswers.range(r.startDate(), r.endDate()) + (r.flagged() ? " ⚠" : ""), "#" + r.id())).toList();
            return clarify(cmd, "requestId", "Which request do you want to " + (approve ? "approve" : "reject") + "?", opts, List.of("requestId"));
        }
        Views.LeaveRequestDetail detail;
        try {
            detail = queries.detail(me, Long.parseLong(id));
        } catch (ApiException | NumberFormatException e) {
            // identical message for "does not exist" and "not visible" → no IDOR oracle
            return new Draft(AssistantAnswers.notFound(id), null, null, List.of(), cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
        }
        String stage = detail.allowedActions().contains(approve ? "MANAGER_APPROVE" : "MANAGER_REJECT") ? "MANAGER"
                : detail.allowedActions().contains(approve ? "HR_APPROVE" : "HR_REJECT") ? "HR" : null;
        if (stage == null) {
            return new Draft("Request #" + id + " is " + AssistantAnswers.statusText(detail.status()) + " — there is nothing for you to "
                    + (approve ? "approve" : "reject") + " on it right now.", null, null, List.of(),
                    cmd.withStatus("ANSWERED", null, List.of()), "ANSWERED");
        }
        String comment = cmd.str("comment");
        if (!approve && (comment == null || comment.trim().length() < 3)) {
            return clarify(cmd, "comment", "What's the reason for rejecting #" + id + "? (It is shared with " + detail.employee().name() + ".)",
                    List.of(), List.of("comment"));
        }
        Views.LeaveRequestSummary summary = queries.summaries(me, List.of(queries.getVisible(me, detail.id()))).get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", detail.id());
        payload.put("stage", stage);
        payload.put("decision", approve ? "APPROVE" : "REJECT");
        payload.put("comment", comment);
        boolean flagged = detail.conflict() != null && detail.conflict().flagged();
        String msg = (approve ? "Approve" : "Reject") + " #" + detail.id() + " — " + detail.employee().name() + ", "
                + AssistantAnswers.typeName(detail.leaveTypeCode()) + " " + AssistantAnswers.range(detail.startDate(), detail.endDate())
                + " (" + stage.toLowerCase() + " stage)?"
                + (flagged && approve ? " It is flagged for high team absence — you'll need to acknowledge that." : "")
                + (approve && "MANAGER".equals(stage) ? " After your approval it still goes to HR." : "")
                + " Confirm to proceed — nothing has changed yet.";
        ProposedAction p = new ProposedAction("DECIDE_REQUEST", payload, null, summary, detail.conflict(), true);
        return new Draft(msg, null, p, List.of(), cmd.withStatus("READY_FOR_CONFIRMATION", null, List.of()), "PROPOSED");
    }

    // =========================================================== helpers
    private static Draft clarify(CanonicalCommand cmd, String slot, String question, List<Option> options, List<String> missing) {
        return new Draft(question, new Clarification(slot, question, options), null, List.of(),
                cmd.withStatus("NEEDS_CLARIFICATION", slot, missing), "CLARIFY");
    }

    private static String policyDenied(Intent intent) {
        return switch (intent) {
            case APPROVE_REQUEST, REJECT_REQUEST -> "Only managers and HR can approve or reject requests.";
            case QUERY_PENDING_APPROVALS, QUERY_CONFLICTS, QUERY_ESCALATIONS, QUERY_TEAM_LEAVE -> "That information is only available to managers and HR.";
            case QUERY_LEAVE_OVERVIEW -> "The organisation-wide leave overview is only available to HR.";
            default -> "I can't help with that for your role.";
        };
    }

    static String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) return "en-IN";
        String l = lang.trim();
        if (l.length() == 2) return l.toLowerCase() + "-IN";
        return l.matches("[a-z]{2,3}-[A-Z]{2}") ? l : "en-IN";
    }
}
