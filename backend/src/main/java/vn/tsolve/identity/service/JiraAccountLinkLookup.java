package vn.tsolve.identity.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.identity.dto.LinkedAccount;

/** The Account a ticket tracker account is linked to, for {@code jira-app} and {@code project} (AC-001.77, AC-001.78). */
public interface JiraAccountLinkLookup {

    /**
     * The Active Account linked to the ticket tracker account in the Workspace bound by {@code TenantScope}, with the
     * time of the link; empty when there is no link or the Account is not Active.
     */
    Optional<LinkedAccount> find(String trackerAccountId);

    /**
     * The account id each of these ticket tracker accounts is linked to in the bound Workspace, whatever the account's
     * status; the ids that are not linked are absent (AC-001.78).
     */
    Map<String, UUID> linkedAccountIds(Set<String> trackerAccountIds);
}
