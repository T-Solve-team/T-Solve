package vn.tsolve.ticket.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.tsolve.ticket.dto.IssueStatusChange;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.dto.SolutionSections;

/** The Solution Form Draft of one user for one Jira issue (jira-app STORY-005). */
public interface SolutionDraftService {

    /** Stores the sections, unvalidated, as the creator's only Draft for the issue (AC-005.1). */
    void save(UUID creatorId, UUID projectId, String issueKey, SolutionSections sections);

    /** The creator's own Draft for the issue; another user's Draft is never returned (AC-005.3). */
    Optional<SolutionSections> find(UUID creatorId, UUID projectId, String issueKey);

    /** Whether the issue was last recorded in the Done status category; empty when never recorded (AC-005.9). */
    Optional<Boolean> findIssueDone(UUID projectId, String issueKey);

    /**
     * Records the issue's Done state and, when the change names a Draft owner, creates that owner's Draft unless they
     * already have one — both in one transaction (jira-app AC-005.7 to AC-005.10).
     */
    void recordIssueStatus(IssueStatusChange change);

    /**
     * Every Draft of the bound Workspace whose issue has no ticket of any source, newest saved first; whether the creator
     * is still active is the caller's check (jira-app AC-011.2).
     */
    List<OpenDraft> findOpenDrafts();

    /** One creator's open Drafts, newest saved first (jira-app AC-011.3). */
    List<OpenDraft> findOpenDrafts(UUID creatorId);
}
