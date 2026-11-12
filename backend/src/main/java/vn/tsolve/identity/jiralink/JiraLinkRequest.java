package vn.tsolve.identity.jiralink;

import java.util.UUID;

/** What a Jira link request holds: the Workspace, the tracker account and the two names shown on the confirm page. */
public record JiraLinkRequest(UUID workspaceId, String trackerAccountId, String jiraUserName, String siteName) {}
