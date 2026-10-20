package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.List;

/** Called by a trusted submission integration after membership and review-date validation. */
public record ReviewRegistration(
        String sourceTicketId,
        String title,
        long projectId,
        String projectName,
        ReviewSource source,
        String writerId,
        String writerName,
        SolutionSections solution,
        List<EvidenceReference> evidence,
        Instant enteredQueueAt,
        Instant reviewDueDate) {
    public ReviewRegistration {
        evidence = List.copyOf(evidence);
    }

    public record EvidenceReference(String evidenceId, String name, String mediaType, long sizeBytes) {}
}
