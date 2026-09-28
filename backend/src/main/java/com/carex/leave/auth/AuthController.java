package com.carex.leave.auth;

import com.carex.leave.audit.AuditAction;
import com.carex.leave.audit.AuditService;
import com.carex.leave.common.error.ApiException;
import com.carex.leave.common.error.Errors;
import com.carex.leave.config.AppProperties;
import com.carex.leave.config.RateLimiter;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.OrgService;
import com.carex.leave.org.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    /** Demo quick-login keys → seeded emails (demo profile only; implementation.md §13). */
    public static final String DEMO_DOMAIN = "@demo.carex.app";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final OrgService org;
    private final AppProperties props;
    private final RateLimiter rateLimiter;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                          OrgService org, AppProperties props, RateLimiter rateLimiter) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.org = org;
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {}

    public record DemoLoginRequest(@NotBlank @Size(max = 40) String userKey) {}

    public record UserView(Long id, String name, String email, String role, Long teamId, String teamName,
                           String preferredLanguage, String joiningDate, List<Long> managedTeamIds) {}

    public record LoginResponse(String accessToken, Instant expiresAt, UserView user) {}

    public record DemoAccount(String key, String name, String role, String teamName, String description) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        rateLimiter.checkLogin(http.getRemoteAddr() + "|" + email);
        AppUser user = users.findByEmail(email).filter(AppUser::isActive).orElse(null);
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            audit.recordIsolated(null, "ANONYMOUS", AuditAction.LOGIN_FAILED, "USER",
                    user == null ? null : user.getId(), "Login failed", null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        audit.recordIsolated(CurrentUser.of(user), null, AuditAction.LOGIN_SUCCEEDED, "USER", user.getId(),
                "Login succeeded", null);
        return issue(user);
    }

    @PostMapping("/demo-login")
    public LoginResponse demoLogin(@Valid @RequestBody DemoLoginRequest req) {
        if (!props.demo().enabled()) {
            throw Errors.notFound("Not found");
        }
        String key = req.userKey().trim().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z]{2,20}")) {
            throw Errors.notFound("Unknown demo user");
        }
        AppUser user = users.findByEmail(key + DEMO_DOMAIN).filter(AppUser::isActive)
                .orElseThrow(() -> Errors.notFound("Unknown demo user"));
        audit.recordIsolated(CurrentUser.of(user), null, AuditAction.DEMO_LOGIN, "USER", user.getId(),
                "Demo quick-login", null);
        return issue(user);
    }

    @GetMapping("/demo-accounts")
    public Map<String, Object> demoAccounts() {
        if (!props.demo().enabled()) {
            return Map.of("enabled", false, "accounts", List.of());
        }
        List<DemoAccount> accounts = users.findAll().stream()
                .filter(u -> u.isActive() && u.getEmail().endsWith(DEMO_DOMAIN))
                .sorted((a, b) -> a.getRole() == b.getRole() ? a.getId().compareTo(b.getId()) : a.getRole().compareTo(b.getRole()))
                .map(u -> new DemoAccount(u.getEmail().substring(0, u.getEmail().indexOf('@')), u.getFullName(),
                        u.getRole().name(), org.team(u.getTeamId()).getName(), describe(u)))
                .toList();
        return Map.of("enabled", true, "accounts", accounts);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal CurrentUser me) {
        return view(org.user(me.id()));
    }

    private LoginResponse issue(AppUser user) {
        JwtService.Token token = jwt.issue(user.getId(), user.getRole().name());
        return new LoginResponse(token.value(), token.expiresAt(), view(user));
    }

    private UserView view(AppUser u) {
        return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(), u.getTeamId(),
                org.team(u.getTeamId()).getName(), u.getPreferredLanguage(), u.getJoiningDate().toString(),
                org.teamIdsManagedBy(u.getId()));
    }

    private String describe(AppUser u) {
        List<Long> managed = org.teamIdsManagedBy(u.getId());
        String base = switch (u.getRole()) {
            case EMPLOYEE -> "Employee";
            case MANAGER -> "Manager";
            case HR -> "HR";
        };
        if (!managed.isEmpty()) {
            base += " · leads " + org.team(managed.get(0)).getName();
        }
        return base + " · joined " + u.getJoiningDate();
    }
}
