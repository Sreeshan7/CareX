package com.carex.leave.assistant.provider;

import java.util.Optional;

/** AI_PROVIDER=none (or Sarvam not configured): every capability is unavailable; the app degrades gracefully. */
public final class NoAiProviders {
    private NoAiProviders() {}

    public static final AiPorts.SpeechToText STT = new AiPorts.SpeechToText() {
        public boolean available() { return false; }
        public AiPorts.Transcript transcribe(byte[] a, String m, Optional<String> l) { throw new AiPorts.AiUnavailable("Speech recognition is not configured"); }
    };

    public static final AiPorts.Translation TRANSLATION = new AiPorts.Translation() {
        public boolean available() { return false; }
        public String translate(String t, String s, String d) { throw new AiPorts.AiUnavailable("Translation is not configured"); }
    };

    public static final AiPorts.TextToSpeech TTS = new AiPorts.TextToSpeech() {
        public boolean available() { return false; }
        public AiPorts.Audio synthesize(String t, String l) { throw new AiPorts.AiUnavailable("Text-to-speech is not configured"); }
    };

    public static final AiPorts.ChatLlm LLM = new AiPorts.ChatLlm() {
        public boolean available() { return false; }
        public String complete(String s, String u, int m) { throw new AiPorts.AiUnavailable("LLM is not configured"); }
    };
}
