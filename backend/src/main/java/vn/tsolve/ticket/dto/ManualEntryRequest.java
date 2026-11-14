package vn.tsolve.ticket.dto;

import java.util.List;
import java.util.UUID;

/**
 * The {@code entry} part of a manual entry (UC-22). Every field may be absent: Save checks them in the service
 * (AC-006.3–AC-006.5) and a Draft keeps them as entered. {@code resolvedDate} is text so a Draft can hold a date
 * that is not valid yet; {@code keepEvidenceIds} null keeps every Evidence file of the Draft.
 */
public record ManualEntryRequest(
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
        Long draftId,
        List<UUID> keepEvidenceIds) {}
