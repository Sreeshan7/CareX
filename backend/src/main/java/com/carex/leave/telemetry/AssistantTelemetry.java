package com.carex.leave.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Append-only assistant usage telemetry (intent, outcome, provider, latency). Stores NO transcripts, reasons
 * or audio, and touches no business table. Failures never affect the user.
 */
@Component
public class AssistantTelemetry {
    private static final Logger log = LoggerFactory.getLogger(AssistantTelemetry.class);
    private final JdbcTemplate jdbc;

    public AssistantTelemetry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long userId, String channel, String language, String intent, String outcome, String provider,
                       long latencyMs, String errorCode) {
        try {
            jdbc.update("INSERT INTO assistant_interaction (user_id, channel, language, intent, outcome, provider, latency_ms, error_code) " +
                            "VALUES (?,?,?,?,?,?,?,?)", userId, channel, trim(language, 10), trim(intent, 40), trim(outcome, 30),
                    trim(provider, 20), (int) Math.min(latencyMs, Integer.MAX_VALUE), trim(errorCode, 40));
        } catch (RuntimeException e) {
            log.debug("telemetry write skipped: {}", e.getMessage());
        }
    }

    private static String trim(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }
}
