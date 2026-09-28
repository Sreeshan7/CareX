package com.carex.leave.assistant.provider.sarvam;

import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sarvam request/response shapes — the ONLY class that knows Sarvam field names.
 *
 * <p>Verified 2026-09-28 against the official docs (docs.sarvam.ai: authentication, speech-to-text,
 * translate-text, text-to-speech, chat-completions) AND live calls with the project key:</p>
 * <ul>
 *   <li>Auth: header {@code api-subscription-key}; base {@code https://api.sarvam.ai}</li>
 *   <li>STT: POST /speech-to-text multipart {@code file, model=saaras:v3, language_code} → {@code transcript, language_code,
 *       language_probability}; REST limit &lt; 30 s audio; WebM/WAV/OGG/MP3 accepted</li>
 *   <li>Translate: POST /translate {@code input (≤1000 chars mayura:v1), source_language_code, target_language_code, model}
 *       → {@code translated_text}</li>
 *   <li>TTS: POST /text-to-speech {@code text, target_language_code, model=bulbul:v3, speaker} → {@code audios[0]} base64 WAV</li>
 *   <li>Chat: POST /v1/chat/completions OpenAI-style; model {@code sarvam-105b} ({@code sarvam-30b}/{@code sarvam-m} deprecated);
 *       supports {@code response_format: json_object} and {@code reasoning_effort: null}; content at
 *       {@code choices[0].message.content}</li>
 * </ul>
 */
final class SarvamWireFormat {
    static final int TRANSLATE_MAX_CHARS = 1000;

    private SarvamWireFormat() {}

    // ---- speech-to-text (multipart) ----
    static LinkedMultiValueMap<String, Object> sttRequest(AppProperties.Sarvam cfg, byte[] audio, String mimeType,
                                                         Optional<String> languageHint) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        String ext = mimeType != null && mimeType.contains("wav") ? "wav"
                : mimeType != null && mimeType.contains("ogg") ? "ogg"
                : mimeType != null && (mimeType.contains("mpeg") || mimeType.contains("mp3")) ? "mp3" : "webm";
        form.add("file", SarvamClient.filePart(audio, "speech." + ext));
        form.add("model", cfg.sttModel());
        form.add("language_code", languageHint.filter(l -> !l.isBlank()).orElse("unknown"));
        return form;
    }

    static AiPorts.Transcript sttResponse(JsonNode json) {
        String text = text(json, "transcript");
        if (text == null || text.isBlank()) throw new AiPorts.AiUnavailable("Empty transcript");
        JsonNode p = json.get("language_probability");
        return new AiPorts.Transcript(text.trim(), text(json, "language_code"), p == null || p.isNull() ? null : p.asDouble());
    }

    // ---- translation ----
    static Map<String, Object> translateRequest(AppProperties.Sarvam cfg, String text, String source, String target) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("input", text.length() > TRANSLATE_MAX_CHARS ? text.substring(0, TRANSLATE_MAX_CHARS) : text);
        m.put("source_language_code", source == null ? "auto" : source);
        m.put("target_language_code", target);
        m.put("model", cfg.translateModel());
        return m;
    }

    static String translateResponse(JsonNode json) {
        String out = text(json, "translated_text");
        if (out == null || out.isBlank()) throw new AiPorts.AiUnavailable("Empty translation");
        return out;
    }

    // ---- text-to-speech ----
    static Map<String, Object> ttsRequest(AppProperties.Sarvam cfg, String text, String language) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text.length() > 2500 ? text.substring(0, 2500) : text);
        m.put("target_language_code", language);
        m.put("model", cfg.ttsModel());
        if (cfg.ttsSpeaker() != null && !cfg.ttsSpeaker().isBlank()) m.put("speaker", cfg.ttsSpeaker());
        return m;
    }

    static AiPorts.Audio ttsResponse(JsonNode json) {
        JsonNode audios = json.path("audios");
        String b64 = audios.isArray() && !audios.isEmpty() ? audios.get(0).asText() : null;
        if (b64 == null || b64.isBlank()) throw new AiPorts.AiUnavailable("Empty audio");
        return new AiPorts.Audio(Base64.getDecoder().decode(b64), "audio/wav");
    }

    // ---- chat completion (used ONLY for intent/slot extraction; JSON mode, no reasoning) ----
    static Map<String, Object> chatRequest(AppProperties.Sarvam cfg, String system, String user, int maxTokens) {
        Map<String, Object> m = new HashMap<>();
        m.put("model", cfg.llmModel());
        m.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        m.put("temperature", 0);
        m.put("max_tokens", maxTokens);
        m.put("reasoning_effort", null);
        m.put("response_format", Map.of("type", "json_object"));
        return m;
    }

    static String chatResponse(JsonNode json) {
        String content = json.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) throw new AiPorts.AiUnavailable("Empty LLM response");
        return content;
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
