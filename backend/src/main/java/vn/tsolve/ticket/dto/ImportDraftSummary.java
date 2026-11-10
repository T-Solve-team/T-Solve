package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.UUID;
import vn.tsolve.ticket.entity.TicketSource;

/** One Draft in the list on Import Tickets (AC-004.2); rowCount leaves out Removed rows. */
public record ImportDraftSummary(long previewId, TicketSource source, UUID projectId, long rowCount, Instant savedAt) {}
