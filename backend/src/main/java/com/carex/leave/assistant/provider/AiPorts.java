package com.carex.leave.assistant.provider;

import java.util.Optional;

/**
 * Provider-independent AI ports (implementation.md §18.2). The rest of the assistant depends only on these.
 * Every call may throw {@link AiUnavailable}; callers degrade gracefully.
 */
public final class AiPorts {
    private AiPorts() {}

    public interface SpeechToText {
        boolean available();
        Transcript transcribe(byte[] audio, String mimeType, Optional<String> languageHint);
    }

    public interface Translation {
        boolean available();
        String translate(String text, String sourceLanguage, String targetLanguage);
    }

    public interface TextToSpeech {
        boolean available();
        /** Returns WAV (or provider-native) audio bytes. */
        Audio synthesize(String text, String language);
    }

    public interface ChatLlm {
        boolean available();
        String complete(String systemPrompt, String userContent, int maxTokens);
    }

    public record Transcript(String text, String languageCode, Double confidence) {}

    public record Audio(byte[] bytes, String mimeType) {}

    /** Thrown when a provider is not configured, times out, errors, or its circuit breaker is open. */
    public static class AiUnavailable extends RuntimeException {
        public AiUnavailable(String message) { super(message); }
        public AiUnavailable(String message, Throwable cause) { super(message, cause); }
    }
}
