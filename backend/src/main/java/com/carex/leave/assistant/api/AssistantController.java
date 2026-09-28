package com.carex.leave.assistant.api;

import com.carex.leave.assistant.api.AssistantDtos.Language;
import com.carex.leave.assistant.api.AssistantDtos.MessageRequest;
import com.carex.leave.assistant.api.AssistantDtos.Reply;
import com.carex.leave.assistant.api.AssistantDtos.SpeakRequest;
import com.carex.leave.assistant.api.AssistantDtos.Status;
import com.carex.leave.assistant.api.AssistantDtos.TranscriptView;
import com.carex.leave.assistant.core.AssistantOrchestrator;
import com.carex.leave.assistant.provider.AiPorts;
import com.carex.leave.auth.CurrentUser;
import com.carex.leave.common.error.Errors;
import com.carex.leave.config.AppProperties;
import com.carex.leave.config.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * /api/v1/assistant/** — read-only + proposals. No endpoint here mutates leave data; confirmed proposals are
 * executed by the regular endpoints. Sarvam is called server-side only (the browser never sees the key).
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private static final Set<String> AUDIO_TYPES = Set.of("audio/webm", "audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg",
            "audio/mp3", "audio/ogg", "audio/mp4", "audio/aac", "audio/flac");
    private static final long MAX_AUDIO = 2 * 1024 * 1024;
    public static final List<Language> LANGUAGES = List.of(
            new Language("en-IN", "English"), new Language("hi-IN", "हिन्दी"), new Language("ta-IN", "தமிழ்"),
            new Language("te-IN", "తెలుగు"), new Language("kn-IN", "ಕನ್ನಡ"), new Language("ml-IN", "മലയാളം"),
            new Language("bn-IN", "বাংলা"), new Language("mr-IN", "मराठी"), new Language("gu-IN", "ગુજરાતી"),
            new Language("pa-IN", "ਪੰਜਾਬੀ"), new Language("od-IN", "ଓଡ଼ିଆ"));

    private final AssistantOrchestrator orchestrator;
    private final AiPorts.SpeechToText stt;
    private final AiPorts.TextToSpeech tts;
    private final AiPorts.Translation translation;
    private final AiPorts.ChatLlm llm;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    public AssistantController(AssistantOrchestrator orchestrator, AiPorts.SpeechToText stt, AiPorts.TextToSpeech tts,
                               AiPorts.Translation translation, AiPorts.ChatLlm llm, RateLimiter rateLimiter,
                               AppProperties props) {
        this.orchestrator = orchestrator;
        this.stt = stt;
        this.tts = tts;
        this.translation = translation;
        this.llm = llm;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @GetMapping("/status")
    public Status status() {
        String provider = llm.available() || stt.available() ? "sarvam" : "none";
        return new Status(provider, stt.available(), llm.available(), tts.available(), translation.available(), LANGUAGES);
    }

    @PostMapping("/message")
    public Reply message(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody MessageRequest req) {
        rateLimiter.checkAssistant(me.id());
        return orchestrator.handle(me, req.text().trim(), req.language(), req.draftCommand());
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptView transcribe(@AuthenticationPrincipal CurrentUser me, @RequestPart("audio") MultipartFile audio,
                                     @RequestParam(value = "languageHint", required = false) String languageHint) throws IOException {
        rateLimiter.checkSpeech(me.id());
        if (audio.isEmpty() || audio.getSize() > MAX_AUDIO) {
            throw Errors.badRequest("VALIDATION_FAILED", "Audio must be between 1 byte and 2 MB (≈30 s)");
        }
        String type = audio.getContentType() == null ? "" : audio.getContentType().split(";")[0].trim().toLowerCase();
        if (!AUDIO_TYPES.contains(type)) {
            throw Errors.badRequest("VALIDATION_FAILED", "Unsupported audio type");
        }
        if (!stt.available()) throw Errors.aiUnavailable("Speech recognition is not available — please type instead");
        try {
            String hint = languageHint == null || !languageHint.matches("[a-z]{2,3}-[A-Z]{2}|unknown") ? null : languageHint;
            AiPorts.Transcript t = stt.transcribe(audio.getBytes(), type, Optional.ofNullable(hint));
            return new TranscriptView(t.text(), t.languageCode(), t.confidence());
        } catch (AiPorts.AiUnavailable e) {
            throw Errors.aiUnavailable("Couldn't transcribe the audio — please type instead");
        }
    }

    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody SpeakRequest req) {
        rateLimiter.checkAssistant(me.id());
        if (!tts.available()) throw Errors.aiUnavailable("Text-to-speech is not available");
        try {
            AiPorts.Audio a = tts.synthesize(req.text(), req.languageCode());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.mimeType())).body(a.bytes());
        } catch (AiPorts.AiUnavailable e) {
            throw Errors.aiUnavailable("Text-to-speech failed");
        }
    }
}
