package vn.tsolve.jiraapp.service;

import java.util.Optional;
import vn.tsolve.common.security.TSolveUser;

/**
 * The T-Solve user linked to a Jira account of a site — the asker of the "Ask T-Solve" page. Replaced by identity's
 * JiraAccountLinkLookup (identity AC-001.69 to AC-001.71) when it exists.
 */
public interface JiraAskerLookup {

    /** The linked, active user, or empty when the Jira account is not linked to an active T-Solve user. */
    Optional<TSolveUser> find(String cloudId, String accountId);
}
