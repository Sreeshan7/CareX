package com.carex.leave.assistant.provider.sarvam;

import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sarvam AI adapter boundary (implementation.md §18.7). Server-side only: the API key never reaches the browser
 * and is never logged. Each capability is available only when its key/base URL/auth header/path/model are ALL
 * configured from the official docs. Every call has a timeout and a per-capability circuit breaker
 * (50% failures over 10 calls → open for 30 s). No DB transaction is ever open while calling Sarvam.
 *
 * <p><b>Wire formats are isolated in {@link SarvamWireFormat} and are UNVERIFIED until the Sarvam API documentation
 * is provided</b> — that class is the only code expected to change.</p>
 */
public class SarvamClient {
    private static final Logger log = LoggerFactory.getLogger(SarvamClient.class);

    private final AppProperties.Sarvam cfg;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CircuitBreaker> breakers;

    public SarvamClient(AppProperties.Sarvam cfg) {
        this.cfg = cfg;
        CircuitBreakerConfig cbc = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        this.breakers = Map.of(
                "stt", CircuitBreaker.of("sarvam-stt", cbc),
                "translate", CircuitBreaker.of("sarvam-translate", cbc),
                "tts", CircuitBreaker.of("sarvam-tts", cbc),
                "llm", CircuitBreaker.of("sarvam-llm", cbc));
    }

    private boolean base() {
        return cfg != null && cfg.configured() && notBlank(cfg.baseUrl()) && notBlank(cfg.authHeader());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private RestClient client(Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(f)
                .baseUrl(cfg.baseUrl())
                .defaultHeader(cfg.authHeader(), cfg.apiKey())
                .build();
    }

    private <T> T guarded(String capability, Supplier<T> call) {
        CircuitBreaker cb = breakers.get(capability);
        long t0 = System.nanoTime();
        try {
            T out = cb.executeSupplier(call);
            log.info("ai.call cap={} provider=SARVAM ms={} ok=true", capability, (System.nanoTime() - t0) / 1_000_000);
            return out;
        } catch (CallNotPermittedException e) {
            log.warn("ai.call cap={} provider=SARVAM breaker=OPEN", capability);
            throw new AiPorts.AiUnavailable("Sarvam " + capability + " temporarily unavailable (circuit open)");
        } catch (AiPorts.AiUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            // never include request/response bodies or headers (the key) in logs
            log.warn("ai.call cap={} provider=SARVAM ms={} ok=false error={}", capability,
                    (System.nanoTime() - t0) / 1_000_000, e.getClass().getSimpleName());
            throw new AiPorts.AiUnavailable("Sarvam " + capability + " call failed", e);
        }
    }

    public String breakerState(String capability) {
        return breakers.get(capability).getState().name();
    }

    // ------------------------------------------------------------------ capabilities
    public AiPorts.SpeechToText speechToText() {
        return new AiPorts.SpeechToText() {
            public boolean available() {
                return base() && notBlank(cfg.sttPath()) && notBlank(cfg.sttModel()) && !open("stt");
            }

            public AiPorts.Transcript transcribe(byte[] audio, String mimeType, Optional<String> hint) {
                if (!available()) throw new AiPorts.AiUnavailable("Speech recognition is not configured");
                return guarded("stt", () -> {
                    LinkedMultiValueMap<String, Object> form = SarvamWireFormat.sttRequest(cfg, audio, mimeType, hint);
                    String body = client(Duration.ofSeconds(12)).post().uri(cfg.sttPath())
                            .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(String.class);
                    return SarvamWireFormat.sttResponse(read(body));
                });
            }
        };
    }

    public AiPorts.Translation translation() {
        return new AiPorts.Translation() {
            public boolean available() {
                return base() && notBlank(cfg.translatePath()) && notBlank(cfg.translateModel()) && !open("translate");
            }

            public String translate(String text, String source, String target) {
                if (!available()) throw new AiPorts.AiUnavailable("Translation is not configured");
                return guarded("translate", () -> {
                    String body = client(Duration.ofSeconds(6)).post().uri(cfg.translatePath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(SarvamWireFormat.translateRequest(cfg, text, source, target))
                            .retrieve().body(String.class);
                    return SarvamWireFormat.translateResponse(read(body));
                });
            }
        };
    }

    public AiPorts.TextToSpeech textToSpeech() {
        return new AiPorts.TextToSpeech() {
            public boolean available() {
                return base() && notBlank(cfg.ttsPath()) && notBlank(cfg.ttsModel()) && !open("tts");
            }

            public AiPorts.Audio synthesize(String text, String language) {
                if (!available()) throw new AiPorts.AiUnavailable("Text-to-speech is not configured");
                return guarded("tts", () -> {
                    String body = client(Duration.ofSeconds(8)).post().uri(cfg.ttsPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(SarvamWireFormat.ttsRequest(cfg, text, language))
                            .retrieve().body(String.class);
                    return SarvamWireFormat.ttsResponse(read(body));
                });
            }
        };
    }

    public AiPorts.ChatLlm chatLlm() {
        return new AiPorts.ChatLlm() {
            public boolean available() {
                return base() && notBlank(cfg.chatPath()) && notBlank(cfg.llmModel()) && !open("llm");
            }

            public String complete(String systemPrompt, String userContent, int maxTokens) {
                if (!available()) throw new AiPorts.AiUnavailable("LLM is not configured");
                return guarded("llm", () -> {
                    String body = client(Duration.ofSeconds(10)).post().uri(cfg.chatPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(SarvamWireFormat.chatRequest(cfg, systemPrompt, userContent, maxTokens))
                            .retrieve().body(String.class);
                    return SarvamWireFormat.chatResponse(read(body));
                });
            }
        };
    }

    private boolean open(String capability) {
        return breakers.get(capability).getState() == CircuitBreaker.State.OPEN;
    }

    private JsonNode read(String body) {
        try {
            return mapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new AiPorts.AiUnavailable("Unparseable Sarvam response");
        }
    }

    /** Multipart file part with a filename (required by most multipart parsers). */
    static ByteArrayResource filePart(byte[] audio, String filename) {
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
