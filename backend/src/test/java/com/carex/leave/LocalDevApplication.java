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
        loadDotEnv();
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

    /** Loads KEY=VALUE lines from the gitignored repo-root .env (e.g. SARVAM_API_KEY) as system properties. */
    private static void loadDotEnv() throws java.io.IOException {
        for (java.nio.file.Path p : new java.nio.file.Path[]{java.nio.file.Path.of("../.env"), java.nio.file.Path.of(".env")}) {
            if (!java.nio.file.Files.exists(p)) continue;
            for (String line : java.nio.file.Files.readAllLines(p)) {
                String l = line.trim();
                int eq = l.indexOf('=');
                if (l.isEmpty() || l.startsWith("#") || eq < 1) continue;
                String key = l.substring(0, eq).trim();
                if (key.startsWith("VERCEL") || System.getenv(key) != null) continue; // deployment tokens are not app config
                System.setProperty(key, l.substring(eq + 1).trim());
            }
            return;
        }
    }
}
