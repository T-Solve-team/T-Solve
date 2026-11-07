package vn.tsolve.search.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketFeedbackRequest(
        @NotNull Boolean isHelpful, @Size(max = 500) @Schema(nullable = true) String comment) {}
