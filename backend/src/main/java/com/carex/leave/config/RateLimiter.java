package com.carex.leave.config;

import com.carex.leave.common.error.Errors;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token buckets (Bucket4j). Per-instance and reset on restart — acceptable for a single-instance
 * deployment (implementation.md §19, risk S8 documented).
 */
@Component
public class RateLimiter {
    private final boolean enabled;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        this.enabled = props.rateLimit() == null || props.rateLimit().enabled();
    }

    /** 5 login attempts per minute per IP+email. */
    public void checkLogin(String key) {
        check("login:" + key, 5);
    }

    /** 20 assistant calls per minute per user. */
    public void checkAssistant(Long userId) {
        check("assistant:" + userId, 20);
    }

    /** 10 transcriptions per minute per user. */
    public void checkSpeech(Long userId) {
        check("stt:" + userId, 10);
    }

    private void check(String key, int perMinute) {
        if (!enabled) {
            return;
        }
        if (buckets.size() > 50_000) {
            buckets.clear(); // crude memory bound
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw Errors.rateLimited().with("retryAfterSeconds", 60);
        }
    }
}
