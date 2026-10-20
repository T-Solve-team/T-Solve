package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.List;
import vn.tsolve.ticket.dto.ReviewQueueItem.ProjectRef;
import vn.tsolve.ticket.dto.ReviewRegistration.EvidenceReference;

public record ReviewTicketResponse(
        Long ticketId,
        String sourceTicketId,
        String title,
        ProjectRef project,
        ReviewSource source,
        String writerId,
        String authorName,
        ReviewStatus status,
        Instant enteredQueueAt,
        Instant reviewDueDate,
        Instant updatedAt,
        String reviewComment,
        SolutionSections solution,
        List<EvidenceReference> evidence) {}
