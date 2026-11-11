package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.dto.JiraIssueContext;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.project.service.ProjectService;

@Service
public class JiraIssueAccessImpl implements JiraIssueAccess {

    private final JiraWorkspaceLookup workspaces;
    private final TenantScope tenantScope;
    private final JiraAskerLookup users;
    private final ProjectService projects;
    private final ScopeAccessService scopes;

    public JiraIssueAccessImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            JiraAskerLookup users,
            ProjectService projects,
            ScopeAccessService scopes) {
        this.workspaces = workspaces;
        this.tenantScope = tenantScope;
        this.users = users;
        this.projects = projects;
        this.scopes = scopes;
    }

    @Override
    public <T> T inIssue(JiraAppPrincipal caller, String issueKey, Function<JiraIssueContext, T> work) {
        if (caller == null || caller.accountId() == null || caller.accountId().isBlank()) {
            throw new JiraIssueAccessDeniedException();
        }
        UUID workspace = workspaces.workspaceOf(caller.cloudId()).orElseThrow(JiraIssueAccessDeniedException::new);
        return tenantScope.runIn(workspace, () -> work.apply(context(caller, issueKey)));
    }

    private JiraIssueContext context(JiraAppPrincipal caller, String issueKey) {
        Optional<TSolveUser> user = users.find(caller.cloudId(), caller.accountId());
        Optional<UUID> project = projects.findActivePublicIdByJiraProjectKey(projectKey(issueKey));
        // Project membership with the matching Workspace role: MEMBER needs Ticket Resolver, PROJECT_MANAGER needs
        // Project Manager (identity's shared scope rule, KAN-118).
        boolean member = user.isPresent()
                && project.isPresent()
                && scopes.canAccessProject(user.get().id(), project.get());
        return new JiraIssueContext(user, project, member);
    }

    // ITSUP-12 → ITSUP. The controllers have already checked the key against ^[A-Z][A-Z0-9_]*-[1-9][0-9]*$.
    static String projectKey(String issueKey) {
        int dash = issueKey.lastIndexOf('-');
        return dash < 0 ? issueKey : issueKey.substring(0, dash);
    }
}
