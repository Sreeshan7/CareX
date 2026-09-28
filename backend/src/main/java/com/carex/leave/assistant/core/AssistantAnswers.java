package com.carex.leave.assistant.core;

import com.carex.leave.assistant.api.AssistantDtos.Card;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.dashboard.DashboardService;
import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.query.LeaveQueryService;
import com.carex.leave.leave.query.Views.EscalationView;
import com.carex.leave.leave.query.Views.LeaveRequestSummary;
import com.carex.leave.org.Role;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only query handlers + English answer templates (implementation.md §18.9). Every fact comes from an
 * authorized read service called with the current user, so the assistant can only say what that user may see —
 * and it cannot invent numbers, because the text is a template filled with service results.
 */
@Component
public class AssistantAnswers {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final BalanceService balances;
    private final LeaveQueryService queries;
    private final DashboardService dashboard;
    private final WorkingDayCalculator workingDays;
    private final BusinessCalendar calendar;

    public AssistantAnswers(BalanceService balances, LeaveQueryService queries, DashboardService dashboard,
                            WorkingDayCalculator workingDays, BusinessCalendar calendar) {
        this.balances = balances;
        this.queries = queries;
        this.dashboard = dashboard;
        this.workingDays = workingDays;
        this.calendar = calendar;
    }

    public record Answer(String text, List<Card> cards) {
        static Answer of(String text) { return new Answer(text, List.of()); }
    }

    public Answer answer(CurrentUser me, CanonicalCommand cmd) {
        return switch (cmd.intent()) {
            case QUERY_BALANCE -> balance(me);
            case QUERY_MY_REQUESTS -> myRequests(me);
            case QUERY_REQUEST_STATUS -> status(me, cmd);
            case QUERY_PENDING_APPROVALS -> pending(me);
            case QUERY_TEAM_LEAVE -> teamLeave(me);
            case QUERY_CONFLICTS -> conflicts(me);
            case QUERY_ESCALATIONS -> escalations(me);
            case QUERY_LEAVE_OVERVIEW -> overview(me);
            case QUERY_HOLIDAYS -> holidays();
            case POLICY_HELP -> Answer.of(policy());
            case GREETING -> Answer.of(help(me.role()));
            default -> Answer.of(help(me.role()));
        };
    }

    Answer balance(CurrentUser me) {
        int year = calendar.today().getYear();
        var list = balances.snapshots(me.id(), year);
        String parts = list.stream().map(b -> b.leaveTypeName() + ": " + days(b.available()) + " available"
                        + (b.pending().signum() > 0 ? " (" + days(b.pending()) + " pending)" : ""))
                .collect(Collectors.joining("; "));
        return new Answer("Your " + year + " leave balance — " + parts + ".", List.of(new Card("BALANCES", list)));
    }

    Answer myRequests(CurrentUser me) {
        var page = queries.mine(me, null, 0, 5);
        if (page.items().isEmpty()) return Answer.of("You have no leave requests yet.");
        return new Answer("Your latest requests: " + list(page.items(), false) + ".", List.of(new Card("REQUESTS", page.items())));
    }

    Answer status(CurrentUser me, CanonicalCommand cmd) {
        String id = cmd.str("requestId");
        if (id == null) return myRequests(me);
        try {
            var r = queries.getVisible(me, Long.parseLong(id));
            var s = queries.summaries(me, List.of(r)).get(0);
            return new Answer("Request #" + s.id() + " (" + typeName(s.leaveTypeCode()) + ", " + range(s.startDate(), s.endDate())
                    + ") is " + statusText(s.status()) + ".", List.of(new Card("REQUESTS", List.of(s))));
        } catch (ApiException e) {
            return Answer.of(notFound(id));
        }
    }

    Answer pending(CurrentUser me) {
        List<LeaveRequestSummary> q = me.isHr() ? queries.hrQueue(me) : queries.managerQueue(me);
        if (q.isEmpty()) return Answer.of("Nothing is waiting for your approval.");
        long esc = q.stream().filter(LeaveRequestSummary::escalated).count();
        long flagged = q.stream().filter(LeaveRequestSummary::flagged).count();
        return new Answer(q.size() + " request(s) await your decision" + (esc > 0 ? ", " + esc + " escalated" : "")
                + (flagged > 0 ? ", " + flagged + " flagged for team conflict" : "") + ": " + list(q, true) + ".",
                List.of(new Card("REQUESTS", q)));
    }

    Answer teamLeave(CurrentUser me) {
        LocalDate today = calendar.today();
        var cal = queries.teamCalendar(me, today, today.plusDays(13), null);
        if (cal.entries().isEmpty()) return Answer.of("No one in your team" + (me.isHr() ? "s" : "") + " has leave in the next two weeks.");
        Map<Long, String> names = cal.members().stream().collect(Collectors.toMap(m -> m.id(), m -> m.name(), (a, b) -> a));
        String text = cal.entries().stream().limit(8).map(e -> names.getOrDefault(e.employeeId(), "#" + e.employeeId())
                + " " + range(e.startDate(), e.endDate()) + " (" + statusText(e.status()) + ")").collect(Collectors.joining("; "));
        long overDays = cal.daysByTeam().values().stream().flatMap(List::stream).filter(d -> d.over()).count();
        return new Answer("Leave in the next two weeks: " + text + "." + (overDays > 0 ? " " + overDays
                + " day(s) are above the team absence threshold." : ""), List.of(new Card("TEAM_CALENDAR", cal)));
    }

    Answer conflicts(CurrentUser me) {
        var list = queries.conflicts(me);
        if (list.isEmpty()) return Answer.of("There are no flagged team-absence conflicts right now.");
        return new Answer(list.size() + " request(s) are flagged for high team absence (flagged, not rejected): "
                + list(list, true) + ".", List.of(new Card("REQUESTS", list)));
    }

    Answer escalations(CurrentUser me) {
        List<EscalationView> list = queries.escalations(me, true);
        if (list.isEmpty()) return Answer.of("There are no open escalations.");
        String text = list.stream().limit(6).map(e -> "#" + e.requestId() + " " + e.employee().name() + " — "
                + e.stage().toLowerCase() + " stage, escalated to " + (e.target() == null ? "the HR pool" : e.target().name()))
                .collect(Collectors.joining("; "));
        return new Answer(list.size() + " open escalation(s): " + text + ".", List.of(new Card("ESCALATIONS", list)));
    }

    Answer overview(CurrentUser me) {
        var o = dashboard.hrOverview(me, calendar.today().getYear());
        return new Answer("This year: " + o.get("totalRequests") + " requests; " + o.get("pendingHr") + " awaiting HR; "
                + o.get("openEscalations") + " open escalation(s); " + o.get("flaggedActive") + " active flagged request(s); "
                + "average approval time " + o.get("avgApprovalHours") + " h.", List.of(new Card("OVERVIEW", o)));
    }

    Answer holidays() {
        var list = workingDays.upcomingHolidays(calendar.today(), 5);
        if (list.isEmpty()) return Answer.of("No upcoming holidays are configured.");
        return new Answer("Upcoming holidays: " + list.stream().map(h -> D.format(h.getHolidayDate()) + " " + h.getName())
                .collect(Collectors.joining("; ")) + ".", List.of(new Card("HOLIDAYS",
                list.stream().map(h -> Map.of("date", h.getHolidayDate().toString(), "name", h.getName())).toList())));
    }

    public static String policy() {
        return "Every request goes to your manager first, then to HR for the final approval — a manager cannot skip HR. "
                + "Pending requests reserve days from your balance; rejection or cancellation returns them. "
                + "If a stage waits too long it is escalated automatically (to the skip-level manager or HR). "
                + "High team absence is flagged for approvers, never auto-rejected. Mid-year joiners get pro-rated balances.";
    }

    public static String help(Role role) {
        String base = "I can show your balance and requests, help you apply for or cancel leave";
        String extra = switch (role) {
            case MANAGER -> ", and show your approval queue, team leave, conflicts and escalations";
            case HR -> ", and show HR approvals, escalations, conflicts and the leave overview";
            default -> "";
        };
        return base + extra + ". Nothing is submitted until you confirm. Try: \"casual leave next Monday for 3 days\".";
    }

    public static String notFound(String id) {
        return "I couldn't find request #" + id + " among the requests you can access.";
    }

    // ------------------------------------------------------------------ formatting helpers
    static String list(List<LeaveRequestSummary> rows, boolean withName) {
        List<String> out = new ArrayList<>();
        for (LeaveRequestSummary s : rows.stream().limit(5).toList()) {
            out.add("#" + s.id() + (withName ? " " + s.employee().name() : "") + " " + typeName(s.leaveTypeCode()) + " "
                    + range(s.startDate(), s.endDate()) + " — " + statusText(s.status()) + (s.flagged() ? " ⚠" : ""));
        }
        return String.join("; ", out) + (rows.size() > 5 ? " and " + (rows.size() - 5) + " more" : "");
    }

    public static String range(LocalDate a, LocalDate b) {
        return a.equals(b) ? D.format(a) : D.format(a) + " – " + D.format(b);
    }

    public static String date(LocalDate d) {
        return D.format(d);
    }

    static String typeName(String code) {
        return switch (code) {
            case "ANNUAL" -> "Annual";
            case "CASUAL" -> "Casual";
            case "SICK" -> "Sick";
            default -> code;
        };
    }

    static String statusText(String status) {
        return switch (status) {
            case "PENDING_MANAGER" -> "awaiting manager approval";
            case "MANAGER_ESCALATED" -> "escalated at the manager stage";
            case "PENDING_HR" -> "awaiting HR approval";
            case "HR_ESCALATED" -> "escalated at the HR stage";
            case "APPROVED" -> "approved";
            case "REJECTED" -> "rejected";
            case "CANCELLED" -> "cancelled";
            default -> status.toLowerCase();
        };
    }

    static String days(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
