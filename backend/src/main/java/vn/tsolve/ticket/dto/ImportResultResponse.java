package vn.tsolve.ticket.dto;

/** The result of Confirm: the Import Batch, tickets published, rows skipped (Error + Removed) and MSG32. */
public record ImportResultResponse(long importBatchId, int created, long skipped, MessageRef message) {}
