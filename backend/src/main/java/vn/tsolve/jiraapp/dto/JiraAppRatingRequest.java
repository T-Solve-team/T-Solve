package vn.tsolve.jiraapp.dto;

import jakarta.validation.constraints.NotNull;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;

/** The {@code ChatbotRating} of the contract: "Solved" sends HELPFUL, "Not solved" NOT_HELPFUL (AC-010.15). */
public record JiraAppRatingRequest(
        @NotNull(message = "MSG01") ChatbotRating rating) {}
