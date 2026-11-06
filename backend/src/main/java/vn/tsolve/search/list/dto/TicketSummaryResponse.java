package vn.tsolve.search.list.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;

public record TicketSummaryResponse(
        UUID ticketId,
        String sourceTicketId,
        String title,
        TicketProjectRef project,
        TicketDepartmentRef department,
        String source,
        String status,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant reviewDueDate,
        Instant updatedAt) {}
