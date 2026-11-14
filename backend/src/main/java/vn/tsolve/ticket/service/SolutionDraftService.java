package vn.tsolve.ticket.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.ticket.dto.SolutionSections;

/** The Solution Form Draft of one user for one Jira issue (jira-app STORY-005). */
public interface SolutionDraftService {

    /** Stores the sections, unvalidated, as the creator's only Draft for the issue (AC-005.1). */
    void save(UUID creatorId, UUID projectId, String issueKey, SolutionSections sections);

    /** The creator's own Draft for the issue; another user's Draft is never returned (AC-005.3). */
    Optional<SolutionSections> find(UUID creatorId, UUID projectId, String issueKey);
}
