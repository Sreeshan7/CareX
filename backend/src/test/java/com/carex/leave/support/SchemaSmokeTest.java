package com.carex.leave.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSmokeTest {
    @Test
    void flywayMigratesOnRealPostgresWithBtreeGist() throws Exception {
        String url = EmbeddedPg.newDatabaseUrl("smoke");
        Flyway.configure().dataSource(url, "postgres", "postgres").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "postgres", "postgres");
             ResultSet rs = c.createStatement().executeQuery("select count(*) from leave_type")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }
}
