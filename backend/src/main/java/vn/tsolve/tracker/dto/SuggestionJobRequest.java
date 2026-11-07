package vn.tsolve.tracker.dto;

import java.util.UUID;

/**
 * One suggestion job (AC-011.1): only a reference to the suggestion result, whose row holds the issue text. The
 * worker's jobs may carry no ticket content; the issue key is for logs.
 */
public record SuggestionJobRequest(UUID jobRef, String issueKey) {}
