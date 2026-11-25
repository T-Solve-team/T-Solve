package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A web question; the earlier turns come from the conversation {@code chatSessionId}, absent for a new one. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotAskRequest(
        @NotBlank(message = "MSG01") @Size(max = 1000, message = "MSG02")
        String question,

        // A String, so an id that is not a UUID is a 404 (AC-013.4), not a 400.
        String chatSessionId) {}
