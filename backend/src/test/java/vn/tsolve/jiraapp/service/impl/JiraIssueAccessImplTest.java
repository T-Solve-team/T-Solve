package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.dto.JiraIssueContext;
import vn.tsolve.jiraapp.exception.JiraAskerNotLinkedException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.project.service.ProjectService;

class JiraIssueAccessImplTest {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "b@x.test", "Ngo Bao", Set.of());
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-acc-1", null);

    private final JiraWorkspaceLookup workspaces = mock(JiraWorkspaceLookup.class);
    private final BindingRecorder tenantScope = new BindingRecorder();
    private final JiraAskerLookup users = mock(JiraAskerLookup.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final ScopeAccessService scopes = mock(ScopeAccessService.class);
    private final JiraIssueAccessImpl access =
            new JiraIssueAccessImpl(workspaces, tenantScope, users, projects, scopes);

    @Test
    void allowsAMemberOfTheIssuesProjectInsideTheSitesWorkspace_AC_001_2() {
        givenRegisteredSite();
        given(users.find("cloud-1", "jira-acc-1")).willReturn(Optional.of(USER));
        given(projects.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(scopes.canAccessProject(USER.id(), PROJECT)).willReturn(true);

        JiraIssueContext context = access.inIssue(CALLER, "ITSUP-12", c -> c);

        assertThat(context.requireMember()).isEqualTo(new JiraIssueContext.Member(USER, PROJECT));
        assertThat(tenantScope.bound).isEqualTo(WORKSPACE);
    }

    @Test
    void reportsAnUnlinkedAccountAsNotLinked_AC_001_3_AC_008_1() {
        givenRegisteredSite();
        given(users.find("cloud-1", "jira-acc-1")).willReturn(Optional.empty());
        given(projects.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));

        JiraIssueContext context = access.inIssue(CALLER, "ITSUP-12", c -> c);

        assertThat(context.linked()).isFalse();
        assertThat(context.member()).isFalse();
        assertThatThrownBy(context::requireMember).isInstanceOf(JiraAskerNotLinkedException.class);
        verifyNoInteractions(scopes);
    }

    @Test
    void refusesAUserWhoIsNotAMemberWithMsg08_AC_001_3() {
        givenRegisteredSite();
        given(users.find("cloud-1", "jira-acc-1")).willReturn(Optional.of(USER));
        given(projects.findActivePublicIdByJiraProjectKey("ITSUP")).willReturn(Optional.of(PROJECT));
        given(scopes.canAccessProject(USER.id(), PROJECT)).willReturn(false);

        JiraIssueContext context = access.inIssue(CALLER, "ITSUP-12", c -> c);

        assertThat(context.linked()).isTrue();
        assertThatThrownBy(context::requireMember)
                .isInstanceOfSatisfying(
                        CodedException.class, e -> assertThat(e.getCode()).isEqualTo("MSG08"));
    }

    @Test
    void hasNoProjectForAKeyOfNoActiveProject_AC_001_2() {
        givenRegisteredSite();
        given(users.find("cloud-1", "jira-acc-1")).willReturn(Optional.of(USER));
        given(projects.findActivePublicIdByJiraProjectKey("OTHER")).willReturn(Optional.empty());

        JiraIssueContext context = access.inIssue(CALLER, "OTHER-1", c -> c);

        assertThat(context.projectId()).isEmpty();
        assertThat(context.member()).isFalse();
        verify(scopes, never()).canAccessProject(any(), any());
    }

    @Test
    void refusesASiteWithNoRegisteredWorkspaceBeforeReadingAnything_AC_001_3() {
        given(workspaces.workspaceOf("cloud-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> access.inIssue(CALLER, "ITSUP-12", c -> c))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThat(tenantScope.bound).isNull();
        verifyNoInteractions(users, projects, scopes);
    }

    @Test
    void refusesACallWithoutAJiraUser_AC_001_3() {
        JiraAppPrincipal appCall = new JiraAppPrincipal("cloud-1", null, null);

        assertThatThrownBy(() -> access.inIssue(appCall, "ITSUP-12", c -> c))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThatThrownBy(() -> access.inIssue(null, "ITSUP-12", c -> c))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verifyNoInteractions(workspaces, users);
    }

    @Test
    void takesTheProjectKeyBeforeTheLastDash() {
        assertThat(JiraIssueAccessImpl.projectKey("ITSUP-12")).isEqualTo("ITSUP");
        assertThat(JiraIssueAccessImpl.projectKey("OPS_2-7")).isEqualTo("OPS_2");
    }

    private void givenRegisteredSite() {
        given(workspaces.workspaceOf("cloud-1")).willReturn(Optional.of(WORKSPACE));
    }

    /** Records which Workspace the work ran in; the real scope only routes the database. */
    private static final class BindingRecorder implements TenantScope {
        private UUID bound;

        @Override
        public UUID currentWorkspaceId() {
            return bound;
        }

        @Override
        public <T> T runIn(UUID workspaceId, Supplier<T> work) {
            bound = workspaceId;
            return work.get();
        }
    }
}
