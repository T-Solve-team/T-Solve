package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A Solution Form Draft whose issue has no ticket yet, as the Draft reminder lists it (jira-app STORY-011). Carries no
 * Solution text.
 */
public record OpenDraft(UUID creatorId, String issueKey, Instant updatedAt) {}
