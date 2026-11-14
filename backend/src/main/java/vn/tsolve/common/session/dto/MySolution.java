package vn.tsolve.common.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One row of My Solutions: a Ticket whose Solution the signed-in Account wrote. */
public record MySolution(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ticketId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ticketKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID projectId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String projectName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TicketStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canOpen,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Null when there is no decision yet.")
        LatestDecision latestDecision) {

    public record LatestDecision(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewDecision action,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            String comment,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant decidedAt) {}
}
