package com.carex.leave;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Local development launcher without Docker or a PostgreSQL install: starts a real embedded PostgreSQL 16,
 * then the app with the demo profile (seeded org + requests, 3-minute... see application-demo.yml).
 *
 * Run: mvn spring-boot:test-run   (from backend/)
 * Env overrides honoured: JWT_SECRET, DEMO_USER_PASSWORD, AI_PROVIDER, SARVAM_API_KEY, LOCAL_PG_PORT.
 */
public class LocalDevApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("LOCAL_PG_PORT", "54329"));
        EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(port).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { pg.close(); } catch (Exception ignored) { }
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        if (System.getenv("JWT_SECRET") == null) {
            byte[] key = new byte[48];
            new SecureRandom().nextBytes(key);
            System.setProperty("app.jwt.secret", Base64.getEncoder().encodeToString(key));
        }
        if (System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "demo");
        }
        SpringApplication.run(LeaveApplication.class, args);
    }
}
