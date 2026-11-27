package vn.tsolve.tracker.service;

import java.util.List;

/** Reads of a Jira issue as the Workspace's service account (STORY-014). */
public interface TrackerIssueService {

    /**
     * The plain text of the issue's newest 100 comments, oldest first, without empty ones and the service account's
     * own; an empty list when the read fails (AC-014.1, AC-014.2).
     */
    List<String> comments(String issueKey);
}
