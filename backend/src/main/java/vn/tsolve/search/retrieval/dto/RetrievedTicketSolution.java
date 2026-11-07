package vn.tsolve.search.retrieval.dto;

import java.util.UUID;

/** A Department Solution for the AI Solution Suggestion (AC-011.2); {@code ticketId} is {@code ticket.public_id}. */
public record RetrievedTicketSolution(
        UUID ticketId,
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scopeOfApplicability,
        double similarity) {}
