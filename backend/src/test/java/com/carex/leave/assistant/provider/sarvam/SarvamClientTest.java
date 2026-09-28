package com.carex.leave.assistant.provider.sarvam;

import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adapter boundary against a local mock server: auth header, request shapes, parsing, failures, circuit breaker. */
class SarvamClientTest {
    HttpServer server;
    final Map<String, String> lastBody = new ConcurrentHashMap<>();
    final Map<String, String> lastAuth = new ConcurrentHashMap<>();
    volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route("/speech-to-text", "{\"request_id\":\"r\",\"transcript\":\"casual leave next monday\",\"language_code\":\"en-IN\",\"language_probability\":0.99}");
        route("/translate", "{\"translated_text\":\"வணக்கம்\",\"source_language_code\":\"en-IN\"}");
        route("/text-to-speech", "{\"audios\":[\"" + Base64.getEncoder().encodeToString("RIFFfake".getBytes()) + "\"]}");
        route("/v1/chat/completions", "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"QUERY_BALANCE\\\"}\",\"reasoning_content\":null}}]}");
        server.start();
    }

    void route(String path, String response) {
        server.createContext(path, ex -> {
            lastBody.put(path, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.put(path, String.valueOf(ex.getRequestHeaders().getFirst("api-subscription-key")));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, status == 200 ? out.length : -1);
            if (status == 200) ex.getResponseBody().write(out);
            ex.close();
        });
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    AppProperties.Sarvam cfg(String key) {
        return new AppProperties.Sarvam(key, "http://127.0.0.1:" + server.getAddress().getPort(), "api-subscription-key",
                "/speech-to-text", "/translate", "/text-to-speech", "/v1/chat/completions",
                "saaras:v3", "sarvam-105b", "bulbul:v3", "mayura:v1", "shubh");
    }

    @Test
    void notConfiguredMeansUnavailable() {
        SarvamClient c = new SarvamClient(cfg(""));
        assertThat(c.chatLlm().available()).isFalse();
        assertThat(c.speechToText().available()).isFalse();
        assertThatThrownBy(() -> c.translation().translate("x", "en-IN", "ta-IN")).isInstanceOf(AiPorts.AiUnavailable.class);
        assertThat(cfg("secret-key").toString()).doesNotContain("secret-key"); // key never printed
    }

    @Test
    void verifiedWireFormats() {
        SarvamClient c = new SarvamClient(cfg("test-key"));
        AiPorts.Transcript t = c.speechToText().transcribe(new byte[]{1, 2, 3}, "audio/webm", Optional.of("en-IN"));
        assertThat(t.text()).isEqualTo("casual leave next monday");
        assertThat(t.confidence()).isEqualTo(0.99);
        assertThat(lastBody.get("/speech-to-text")).contains("name=\"file\"").contains("saaras:v3").contains("language_code");
        assertThat(lastAuth.get("/speech-to-text")).isEqualTo("test-key");

        assertThat(c.translation().translate("Hello", "en-IN", "ta-IN")).isEqualTo("வணக்கம்");
        assertThat(lastBody.get("/translate")).contains("\"input\":\"Hello\"").contains("\"target_language_code\":\"ta-IN\"").contains("mayura:v1");

        assertThat(new String(c.textToSpeech().synthesize("Hi", "en-IN").bytes())).isEqualTo("RIFFfake");
        assertThat(lastBody.get("/text-to-speech")).contains("\"target_language_code\":\"en-IN\"").contains("bulbul:v3").contains("shubh");

        assertThat(c.chatLlm().complete("sys", "user", 100)).isEqualTo("{\"intent\":\"QUERY_BALANCE\"}");
        assertThat(lastBody.get("/v1/chat/completions")).contains("sarvam-105b").contains("json_object").contains("\"reasoning_effort\":null");
    }

    @Test
    void failuresBecomeAiUnavailableAndOpenTheBreaker() {
        SarvamClient c = new SarvamClient(cfg("test-key"));
        status = 500;
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> c.translation().translate("x", "en-IN", "hi-IN")).isInstanceOf(AiPorts.AiUnavailable.class);
        }
        assertThat(c.breakerState("translate")).isEqualTo("OPEN");
        assertThat(c.translation().available()).isFalse(); // status endpoint will report it; UI degrades
        assertThat(c.chatLlm().available()).isTrue();      // breakers are per capability
    }
}
