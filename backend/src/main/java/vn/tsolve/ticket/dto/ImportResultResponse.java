package vn.tsolve.ticket.dto;

/**
 * The result of Confirm: the Import Batch, tickets published, rows skipped (CSV: Error + Removed; Jira: issues that
 * already had a Ticket), MSG32, and for Jira the MSG38 notice naming the skipped issues (null when none).
 */
public record ImportResultResponse(
        long importBatchId, int created, long skipped, MessageRef message, MessageRef notice) {}
