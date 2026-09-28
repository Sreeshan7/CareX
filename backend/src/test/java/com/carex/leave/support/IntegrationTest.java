package com.carex.leave.support;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.auth.JwtService;
import com.carex.leave.auth.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

/**
 * Boots the full application against a real embedded PostgreSQL 16 (Flyway-migrated).
 * All subclasses share one Spring context and one database; each test starts from a truncated DB + fresh org.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String url = EmbeddedPg.newDatabaseUrl("it");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        r.add("app.jwt.secret", () -> "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg5MA==");
        r.add("app.escalation.enabled", () -> "false");
        r.add("app.demo.enabled", () -> "false");
        r.add("app.rate-limit.enabled", () -> "false");
        r.add("app.ai.provider", () -> "fake");
    }

    @TestConfiguration
    public static class TestBeans {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        public TestOrg testOrg() {
            return new TestOrg();
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected TestOrg org;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserDirectory directory;
    @Autowired private Clock appClock;

    @BeforeEach
    void resetDatabase() {
        clock.reset();
        jdbc.execute("TRUNCATE notification, approval_action, conflict_flag, escalation, leave_request, leave_balance, " +
                     "assistant_interaction, audit_log, app_user, team RESTART IDENTITY CASCADE");
        directory.evictAll();
        org.create();
    }

    protected String bearer(CurrentUser u) {
        return "Bearer " + jwtService.issue(u.id(), u.role().name()).value();
    }
}
