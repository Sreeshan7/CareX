package com.carex.leave.demo;

import com.carex.leave.auth.AuthController;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.config.AppProperties;
import com.carex.leave.leave.calendar.WorkingDayCalculator;
import com.carex.leave.leave.request.LeaveRequestRepository;
import com.carex.leave.leave.request.Stage;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.Role;
import com.carex.leave.org.Team;
import com.carex.leave.org.TeamRepository;
import com.carex.leave.org.UserRepository;
import com.carex.leave.workflow.LeaveWorkflowService;
import com.carex.leave.workflow.LeaveWorkflowService.Decision;
import com.carex.leave.workflow.LeaveWorkflowService.DecisionCommand;
import com.carex.leave.workflow.LeaveWorkflowService.SubmitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demo org + requests (implementation.md §6.1). Runs only when app.demo.enabled=true and the DB has no users.
 * Requests are created THROUGH the real workflow service (real balances, conflicts, audit), not by inserting rows.
 * Passwords come from DEMO_USER_PASSWORD; if unset, a random password is used (demo quick-login still works).
 */
@Component
public class DemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder encoder;
    private final LeaveWorkflowService workflow;
    private final LeaveRequestRepository requests;
    private final WorkingDayCalculator workingDays;
    private final BusinessCalendar calendar;
    private final TransactionTemplate tx;

    public DemoDataSeeder(AppProperties props, UserRepository users, TeamRepository teams, PasswordEncoder encoder,
                          LeaveWorkflowService workflow, LeaveRequestRepository requests,
                          WorkingDayCalculator workingDays, BusinessCalendar calendar,
                          PlatformTransactionManager txManager) {
        this.props = props;
        this.users = users;
        this.teams = teams;
        this.encoder = encoder;
        this.workflow = workflow;
        this.requests = requests;
        this.workingDays = workingDays;
        this.calendar = calendar;
        this.tx = new TransactionTemplate(txManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.demo().enabled()) {
            return;
        }
        if (users.count() > 0) {
            log.info("demo.seed skipped: users already exist");
            return;
        }
        seedOrg();
        seedRequests();
    }

    public void seedOrg() {
        tx.executeWithoutResult(s -> {
            String pw = props.demo().userPassword();
            if (pw == null || pw.isBlank()) {
                byte[] b = new byte[18];
                new SecureRandom().nextBytes(b);
                pw = Base64.getUrlEncoder().encodeToString(b);
                log.warn("DEMO_USER_PASSWORD not set: demo users get a random password (use demo quick-login)");
            }
            String hash = encoder.encode(pw);
            LocalDate today = calendar.today();

            Team leadership = teams.save(new Team("Leadership", new BigDecimal("50.00"), 2));
            Team engineering = teams.save(new Team("Engineering", new BigDecimal("30.00"), 2));
            Team people = teams.save(new Team("People Ops", new BigDecimal("40.00"), 2));

            AppUser dev = user("dev", "Dev Raman", Role.MANAGER, leadership, LocalDate.of(2019, 4, 1), "en-IN", hash);
            AppUser meera = user("meera", "Meera Iyer", Role.MANAGER, leadership, LocalDate.of(2020, 6, 15), "en-IN", hash);
            AppUser hema = user("hema", "Hema Krishnan", Role.HR, leadership, LocalDate.of(2018, 1, 10), "en-IN", hash);
            user("arjun", "Arjun Kumar", Role.EMPLOYEE, engineering, LocalDate.of(2021, 3, 1), "ta-IN", hash);
            user("bala", "Bala Subramanian", Role.EMPLOYEE, engineering, LocalDate.of(2022, 8, 10), "hi-IN", hash);
            user("chitra", "Chitra Nair", Role.EMPLOYEE, engineering, LocalDate.of(2023, 1, 16), "en-IN", hash);
            user("divya", "Divya Menon", Role.EMPLOYEE, engineering, LocalDate.of(2024, 2, 1), "en-IN", hash);
            user("esha", "Esha Patel", Role.EMPLOYEE, engineering, LocalDate.of(2025, 5, 5), "en-IN", hash);
            // Mid-year joiner: joined on the 20th, two months ago (2026-07-20 on the reference date) → pro-rated
            user("farhan", "Farhan Ali", Role.EMPLOYEE, engineering, today.minusMonths(2).withDayOfMonth(20), "en-IN", hash);
            user("harish", "Harish Rao", Role.HR, people, LocalDate.of(2021, 11, 1), "en-IN", hash);
            user("isha", "Isha Gupta", Role.HR, people, LocalDate.of(2023, 6, 1), "en-IN", hash);

            leadership.setManagerId(dev.getId());
            engineering.setManagerId(meera.getId());
            people.setManagerId(hema.getId());
            teams.save(leadership);
            teams.save(engineering);
            teams.save(people);
            log.info("demo.seed org created: 3 teams, {} users", users.count());
        });
    }

    /** Creates the demo requests through the real workflow (each call is its own transaction). */
    public void seedRequests() {
        Map<String, CurrentUser> u = new HashMap<>();
        users.findAll().forEach(x -> u.put(x.getEmail().substring(0, x.getEmail().indexOf('@')), CurrentUser.of(x)));
        LocalDate today = calendar.today();
        try {
            LocalDate arjunStart = workingDays.nextWorkingDayOnOrAfter(today.plusDays(7));
            submit(u.get("arjun"), "CASUAL", arjunStart, 3, "Family wedding in Madurai");

            LocalDate balaStart = workingDays.nextWorkingDayOnOrAfter(arjunStart.plusDays(1));
            submit(u.get("bala"), "ANNUAL", balaStart, 3, "Trip home");

            LocalDate chitraStart = workingDays.nextWorkingDayOnOrAfter(today.plusDays(14));
            Long chitra = submit(u.get("chitra"), "ANNUAL", chitraStart, 2, "Personal errands");
            // Simulate a manager-stage timeout so the escalation scheduler has something to escalate on startup.
            tx.executeWithoutResult(s -> requests.forceDeadline(chitra, calendar.now().minus(Duration.ofMinutes(5))));

            LocalDate divyaStart = workingDays.nextWorkingDayOnOrAfter(today.plusDays(20));
            Long divya = submit(u.get("divya"), "SICK", divyaStart, 1, "Medical procedure");
            workflow.decide(u.get("meera"), divya, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, "Get well soon", true));

            LocalDate eshaStart = workingDays.nextWorkingDayOnOrAfter(today.plusDays(30));
            Long esha = submit(u.get("esha"), "ANNUAL", eshaStart, 2, "Conference");
            workflow.decide(u.get("meera"), esha, new DecisionCommand(Stage.MANAGER, Decision.APPROVE, null, true));
            workflow.decide(u.get("harish"), esha, new DecisionCommand(Stage.HR, Decision.APPROVE, null, true));

            LocalDate divya2 = workingDays.nextWorkingDayOnOrAfter(today.plusDays(40));
            Long rejected = submit(u.get("divya"), "CASUAL", divya2, 1, "Short break");
            workflow.decide(u.get("meera"), rejected, new DecisionCommand(Stage.MANAGER, Decision.REJECT,
                    "Release week — please pick another date", false));
            log.info("demo.seed requests created");
        } catch (RuntimeException e) {
            log.warn("demo.seed requests partially created: {}", e.getMessage());
        }
    }

    private Long submit(CurrentUser who, String type, LocalDate start, int days, String reason) {
        LocalDate end = workingDays.addWorkingDays(start, days);
        return workflow.submit(who, new SubmitCommand(type, start, end, reason, UUID.randomUUID())).request().getId();
    }

    private AppUser user(String key, String name, Role role, Team team, LocalDate joined, String lang, String hash) {
        return users.save(new AppUser(key + AuthController.DEMO_DOMAIN, name, hash, role, team.getId(), joined, lang));
    }
}
