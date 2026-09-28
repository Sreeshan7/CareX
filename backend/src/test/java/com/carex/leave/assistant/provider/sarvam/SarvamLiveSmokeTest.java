package com.carex.leave.assistant.provider.sarvam;

import com.carex.leave.assistant.core.Intent;
import com.carex.leave.assistant.extract.LlmIntentExtractor;
import com.carex.leave.assistant.extract.RawExtraction;
import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real calls to Sarvam. Runs ONLY when SARVAM_API_KEY is set in the environment (never in CI by default).
 * Verifies the adapter + prompt against the live API: translation, TTS→STT round trip, and LLM extraction
 * of a Hindi request.
 */
@EnabledIfEnvironmentVariable(named = "SARVAM_API_KEY", matches = ".+")
class SarvamLiveSmokeTest {
    final SarvamClient client = new SarvamClient(new AppProperties.Sarvam(System.getenv("SARVAM_API_KEY"),
            "https://api.sarvam.ai", "api-subscription-key", "/speech-to-text", "/translate", "/text-to-speech",
            "/v1/chat/completions", "saaras:v3", "sarvam-105b", "bulbul:v3", "mayura:v1", "shubh"));

    @Test
    void translation() {
        String ta = client.translation().translate("Your leave request is approved.", "en-IN", "ta-IN");
        assertThat(ta).isNotBlank().doesNotContain("approved");
    }

    @Test
    void textToSpeechThenSpeechToTextRoundTrip() {
        AiPorts.Audio audio = client.textToSpeech().synthesize("I need casual leave next Monday for three days.", "en-IN");
        assertThat(audio.bytes().length).isGreaterThan(1000);
        AiPorts.Transcript t = client.speechToText().transcribe(audio.bytes(), "audio/wav", Optional.empty());
        assertThat(t.text().toLowerCase()).contains("casual leave").contains("monday");
    }

    @Test
    void llmExtractsHindiApplyRequestWithoutInventingDates() throws Exception {
        LlmIntentExtractor x = new LlmIntentExtractor(client.chatLlm());
        RawExtraction r = x.extract("मुझे अगले सोमवार से तीन दिन की आकस्मिक छुट्टी चाहिए, शादी के लिए",
                LocalDate.parse("2026-09-28"), "EMPLOYEE", EnumSet.allOf(Intent.class), null);
        System.out.println("LLM extraction: " + r);
        assertThat(r.intent()).isEqualTo(Intent.APPLY_LEAVE);
        assertThat(r.leaveType()).isEqualTo("CASUAL");
        assertThat(r.durationDays()).isEqualTo(3);
        assertThat(r.start()).isNotNull();
        assertThat(r.start().expression()).isNotNull();
    }
}
