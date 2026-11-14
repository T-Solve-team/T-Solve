package vn.tsolve.identity.jiralink;

import java.time.Instant;
import java.util.UUID;

/** The link of one Jira account to one Account of the Workspace's database (AC-001.71). */
public record JiraAccountLink(String trackerAccountId, UUID accountId, Instant linkedAt) {}
