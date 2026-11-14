package vn.tsolve.ticket.dto;

import java.util.UUID;

/** The answer to a saved manual entry: the new Ticket's id and MSG32 (AC-006.1). */
public record ManualEntryResponse(UUID ticketId, MessageRef message) {}
