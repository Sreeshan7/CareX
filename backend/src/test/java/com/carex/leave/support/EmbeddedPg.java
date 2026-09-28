package com.carex.leave.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.Statement;

/**
 * One real PostgreSQL 16 process per JVM (zonky binaries — no Docker needed).
 * Integration tests need real Postgres: exclusion constraints, SKIP LOCKED and advisory locks.
 */
public final class EmbeddedPg {
    private static EmbeddedPostgres instance;
    private static int dbCounter = 0;

    private EmbeddedPg() {}

    public static synchronized EmbeddedPostgres get() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder()
                        .setServerConfig("max_connections", "200")
                        .start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { instance.close(); } catch (IOException ignored) { }
                }));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return instance;
    }

    /** Creates a fresh database so each Spring context gets a clean schema. */
    public static synchronized String newDatabaseUrl(String prefix) {
        String name = prefix + "_" + (++dbCounter);
        try (Connection c = get().getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return get().getJdbcUrl("postgres", name);
    }
}
