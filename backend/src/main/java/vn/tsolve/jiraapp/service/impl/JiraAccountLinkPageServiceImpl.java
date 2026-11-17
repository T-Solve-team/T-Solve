package vn.tsolve.jiraapp.service.impl;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.identity.jiralink.JiraLinkRequestService;
import vn.tsolve.identity.service.JiraAccountLinkLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.client.JiraUserClient;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStart;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus.Status;
import vn.tsolve.jiraapp.exception.JiraAccountLinkUnavailableException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.exception.JiraSiteNotConnectedException;
import vn.tsolve.jiraapp.service.JiraAccountLinkPageService;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

/**
 * The site and the Jira account come only from the verified FIT (AC-014.10). The Jira call runs outside any
 * transaction: the app token is read inside the Workspace first, then Jira is called, then identity stores the
 * request.
 */
@Service
public class JiraAccountLinkPageServiceImpl implements JiraAccountLinkPageService {

    private static final Logger log = LoggerFactory.getLogger(JiraAccountLinkPageServiceImpl.class);

    private final JiraWorkspaceLookup workspaces;
    private final TenantScope tenantScope;
    private final JiraAccountLinkLookup links;
    private final JiraServiceAccount jiraAccounts;
    private final JiraUserClient jiraUsers;
    private final JiraLinkRequestService requests;

    public JiraAccountLinkPageServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            JiraAccountLinkLookup links,
            JiraServiceAccount jiraAccounts,
            JiraUserClient jiraUsers,
            JiraLinkRequestService requests) {
        this.workspaces = workspaces;
        this.tenantScope = tenantScope;
        this.links = links;
        this.jiraAccounts = jiraAccounts;
        this.jiraUsers = jiraUsers;
        this.requests = requests;
    }

    @Override
    public JiraAccountLinkStatus status(JiraAppPrincipal caller) {
        String accountId = jiraUser(caller);
        Optional<UUID> workspace = workspaces.workspaceOf(caller.cloudId());
        if (workspace.isEmpty()) {
            return JiraAccountLinkStatus.of(Status.SITE_NOT_CONNECTED);
        }
        return tenantScope.runIn(workspace.get(), () -> links.find(accountId)
                .map(link -> JiraAccountLinkStatus.linked(
                        link.user().fullName(), link.user().email(), link.linkedAt()))
                .orElseGet(() -> JiraAccountLinkStatus.of(Status.NOT_LINKED)));
    }

    @Override
    public JiraAccountLinkStart start(JiraAppPrincipal caller) {
        UUID workspace = workspaces
                .workspaceOf(caller == null ? null : caller.cloudId())
                .orElseThrow(JiraSiteNotConnectedException::new);
        String accountId = jiraUser(caller);
        JiraAccount jira = tenantScope
                .runIn(workspace, jiraAccounts::current)
                .orElseThrow(JiraAccountLinkUnavailableException::new);
        String displayName =
                jiraUsers.displayName(jira, accountId).orElseThrow(JiraAccountLinkUnavailableException::new);
        try {
            return new JiraAccountLinkStart(requests.create(workspace, accountId, displayName, siteName(jira)));
        } catch (DataAccessException | IllegalArgumentException | IllegalStateException e) {
            // class name only: a Redis message may carry the key
            log.error("Jira link request not created ({})", e.getClass().getName());
            throw new JiraAccountLinkUnavailableException();
        }
    }

    // A FIT without a Jira user may not read or start a link (403 MSG08).
    private static String jiraUser(JiraAppPrincipal caller) {
        if (caller == null || caller.accountId() == null || caller.accountId().isBlank()) {
            throw new JiraIssueAccessDeniedException();
        }
        return caller.accountId();
    }

    // The Jira site's name on the confirm page: the host of its URL (bdang798.atlassian.net).
    static String siteName(JiraAccount jira) {
        try {
            String host = URI.create(jira.siteUrl().strip()).getHost();
            return host == null ? jira.siteUrl().strip() : host;
        } catch (IllegalArgumentException e) {
            return jira.siteUrl().strip();
        }
    }
}
