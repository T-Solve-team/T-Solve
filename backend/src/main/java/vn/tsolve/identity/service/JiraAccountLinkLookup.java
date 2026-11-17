package vn.tsolve.identity.service;

import java.util.Optional;
import vn.tsolve.identity.dto.LinkedAccount;

/** The Account a ticket tracker account is linked to, for {@code jira-app} (AC-001.77). */
public interface JiraAccountLinkLookup {

    /**
     * The Active Account linked to the ticket tracker account in the Workspace bound by {@code TenantScope}, with the
     * time of the link; empty when there is no link or the Account is not Active.
     */
    Optional<LinkedAccount> find(String trackerAccountId);
}
