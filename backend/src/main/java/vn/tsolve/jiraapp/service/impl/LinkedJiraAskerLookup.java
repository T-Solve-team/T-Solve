package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.LinkedAccount;
import vn.tsolve.identity.service.JiraAccountLinkLookup;
import vn.tsolve.jiraapp.service.JiraAskerLookup;

/**
 * The T-Solve user of a Jira account: identity's link in the site's Workspace (AC-014.11, identity AC-001.77). The
 * callers bind that Workspace first, so the site needs no further check here.
 */
@Service
public class LinkedJiraAskerLookup implements JiraAskerLookup {

    private final JiraAccountLinkLookup links;

    public LinkedJiraAskerLookup(JiraAccountLinkLookup links) {
        this.links = links;
    }

    @Override
    public Optional<TSolveUser> find(String cloudId, String accountId) {
        return links.find(accountId).map(LinkedAccount::user);
    }
}
