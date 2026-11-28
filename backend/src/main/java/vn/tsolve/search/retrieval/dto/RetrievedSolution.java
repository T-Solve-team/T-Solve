package vn.tsolve.search.retrieval.dto;

import java.util.UUID;

/** A Workspace Solution; {@code ticketId} is {@code ticket.public_id}, for the server side only (AC-010.2). */
public record RetrievedSolution(
        UUID ticketId,
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scopeOfApplicability,
        double similarity) {}
