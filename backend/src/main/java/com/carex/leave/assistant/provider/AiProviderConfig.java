package com.carex.leave.assistant.provider;

import com.carex.leave.assistant.provider.sarvam.SarvamClient;
import com.carex.leave.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the provider implementation from AI_PROVIDER. "sarvam" (or "auto" with a key) wires the Sarvam adapter (each capability is
 * available only once configured); anything else wires the "none" providers. Tests override with @Primary fakes.
 */
@Configuration
public class AiProviderConfig {
    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    public SarvamClient sarvamClient(AppProperties props) {
        return new SarvamClient(props.ai().sarvam());
    }

    private boolean sarvam(AppProperties props) {
        String p = props.ai().provider() == null ? "auto" : props.ai().provider().toLowerCase();
        return p.equals("sarvam") || (p.equals("auto") && props.ai().sarvam() != null && props.ai().sarvam().configured());
    }

    @Bean
    public AiPorts.SpeechToText speechToText(AppProperties props, SarvamClient client) {
        return sarvam(props) ? client.speechToText() : NoAiProviders.STT;
    }

    @Bean
    public AiPorts.Translation translation(AppProperties props, SarvamClient client) {
        return sarvam(props) ? client.translation() : NoAiProviders.TRANSLATION;
    }

    @Bean
    public AiPorts.TextToSpeech textToSpeech(AppProperties props, SarvamClient client) {
        return sarvam(props) ? client.textToSpeech() : NoAiProviders.TTS;
    }

    @Bean
    public AiPorts.ChatLlm chatLlm(AppProperties props, SarvamClient client) {
        if (sarvam(props)) {
            log.info("AI provider: sarvam ({})", props.ai().sarvam());
            return client.chatLlm();
        }
        log.info("AI provider: none — assistant runs in rule-based, English-only mode");
        return NoAiProviders.LLM;
    }
}
