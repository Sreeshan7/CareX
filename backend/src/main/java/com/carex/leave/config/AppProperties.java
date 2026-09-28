package com.carex.leave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String zone,
        Jwt jwt,
        Escalation escalation,
        Demo demo,
        Cors cors,
        RateLimit rateLimit,
        Ai ai) {

    public record Jwt(String secret, Duration ttl) {}

    public record Escalation(Duration managerTimeout, Duration hrTimeout, Duration pollInterval,
                             Duration initialDelay, int batchSize, boolean enabled) {}

    public record Demo(boolean enabled, String userPassword) {}

    public record Cors(List<String> allowedOrigins) {}

    public record RateLimit(boolean enabled) {}

    public record Ai(String provider, boolean translateBeforeExtract, Sarvam sarvam) {}

    public record Sarvam(String apiKey, String baseUrl, String authHeader,
                         String sttPath, String translatePath, String ttsPath, String chatPath,
                         String sttModel, String llmModel, String ttsModel, String translateModel,
                         String ttsSpeaker) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() { // never print the key
            return "Sarvam[baseUrl=" + baseUrl + ", configured=" + configured() + "]";
        }
    }
}
