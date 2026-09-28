package com.carex.leave.assistant.api;

import com.carex.leave.assistant.core.CanonicalCommand;
import com.carex.leave.leave.query.Views;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AssistantDtos {
    private AssistantDtos() {}

    public record MessageRequest(@NotBlank @Size(max = 500) String text, @Size(max = 10) String language,
                                 CanonicalCommand draftCommand) {}

    public record SpeakRequest(@NotBlank @Size(max = 1000) String text, @NotBlank @Size(max = 10) String languageCode) {}

    public record Option(String label, String value) {}

    public record Clarification(String slot, String question, List<Option> options) {}

    /**
     * A proposal only. The assistant NEVER executes it: the user reviews/edits it in the UI and the UI then calls
     * the normal REST endpoint (/leave-requests, /decisions, /cancel), which re-applies every rule.
     */
    public record ProposedAction(String type, Map<String, Object> payload, Views.PreviewResponse preview,
                                 Views.LeaveRequestSummary request, Views.ConflictView conflict,
                                 boolean requiresConfirmation) {}

    public record Card(String kind, Object data) {}

    public record Reply(String replyText, String replyTextEnglish, String replyLanguage, CanonicalCommand command,
                        Clarification clarification, ProposedAction proposedAction, List<Card> cards,
                        boolean degraded, String degradedReason) {}

    public record Language(String code, String name) {}

    public record Status(String provider, boolean sttAvailable, boolean llmAvailable, boolean ttsAvailable,
                         boolean translateAvailable, List<Language> languages) {}

    public record TranscriptView(String transcript, String languageCode, Double confidence) {}
}
