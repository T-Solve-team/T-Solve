package vn.tsolve.ticket.dto;

import java.util.UUID;

/**
 * A manual entry (STORY-006). No Bean Validation: the service reports every invalid field at once (AC-006.6), and Save
 * as draft keeps the fields as typed — so resolvedDate stays a string until Save parses it.
 */
public record ManualTicketRequest(
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
        Long draftId) {}
