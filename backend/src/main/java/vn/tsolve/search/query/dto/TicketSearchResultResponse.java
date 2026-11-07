package vn.tsolve.search.query.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;

public record TicketSearchResultResponse(
        UUID ticketId,
        String title,
        TicketProjectRef project,
        TicketDepartmentRef department,
        String excerpt,
        Integer helpfulRate,
        OffsetDateTime resolvedAt) {}
