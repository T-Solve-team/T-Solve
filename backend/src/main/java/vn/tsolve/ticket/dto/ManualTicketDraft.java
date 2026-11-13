package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.UUID;

/** A Manual Draft reopened from the Drafts list (AC-006.11); resolvedDate as typed. */
public record ManualTicketDraft(
        long previewId,
        UUID projectId,
        String ticketId,
        String title,
        String description,
        String resolvedDate,
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scope,
        Instant savedAt) {}
