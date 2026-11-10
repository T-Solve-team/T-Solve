package vn.tsolve.ticket.dto;

import java.time.Instant;

/** The answer to Save as draft: MSG41 (AC-004.1). */
public record DraftSavedResponse(long previewId, Instant savedAt, MessageRef message) {}
