package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A Manual Draft as saved, to fill the Manual entry tab again (AC-007.4). */
public record ManualDraftResponse(
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
        List<DraftEvidenceResponse> evidence,
        Instant savedAt) {}
