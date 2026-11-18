package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.LinkedAccount;
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
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

class JiraAccountLinkPageServiceImplTest {

    private static final UUID WORKSPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);
    private static final JiraAccount JIRA = new JiraAccount("https://acme.atlassian.net/", "cloud-1", "token");
    private static final TSolveUser USER = new TSolveUser(
            UUID.fromString("10000000-0000-0000-0000-000000000001"), "an@acme.example", "An Nguyen", Set.of());
    private static final Instant LINKED_AT = Instant.parse("2026-11-17T08:00:00Z");
    private static final String URL = "https://acme.tsolve.io.vn/jira-link?request=abc";

    private final JiraWorkspaceLookup workspaces = mock(JiraWorkspaceLookup.class);
    private final TenantScope tenantScope = mock(TenantScope.class);
    private final JiraAccountLinkLookup links = mock(JiraAccountLinkLookup.class);
    private final JiraServiceAccount jiraAccounts = mock(JiraServiceAccount.class);
    private final JiraUserClient jiraUsers = mock(JiraUserClient.class);
    private final JiraLinkRequestService requests = mock(JiraLinkRequestService.class);
    private final JiraAccountLinkPageServiceImpl service =
            new JiraAccountLinkPageServiceImpl(workspaces, tenantScope, links, jiraAccounts, jiraUsers, requests);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runInTheWorkspace() {
        when(tenantScope.runIn(eq(WORKSPACE), any()))
                .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.of(WORKSPACE));
    }

    @Test
    void answersLinkedWithTheAccountsNameEmailAndLinkTime_AC_014_2() {
        when(links.find("jira-1")).thenReturn(Optional.of(new LinkedAccount(USER, LINKED_AT)));

        assertThat(service.status(CALLER))
                .isEqualTo(JiraAccountLinkStatus.linked("An Nguyen", "an@acme.example", LINKED_AT));
        verify(tenantScope).runIn(eq(WORKSPACE), any());
    }

    @Test
    void answersNotLinkedWithoutAnActiveLink_AC_014_2() {
        when(links.find("jira-1")).thenReturn(Optional.empty());

        assertThat(service.status(CALLER).status()).isEqualTo(Status.NOT_LINKED);
    }

    @Test
    void answersSiteNotConnectedForASiteOfNoWorkspace_AC_014_2() {
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());

        assertThat(service.status(CALLER)).isEqualTo(JiraAccountLinkStatus.of(Status.SITE_NOT_CONNECTED));
        verifyNoInteractions(links);
    }

    @Test
    void refusesTheStatusOfAFitWithoutAJiraUser_AC_014_9() {
        assertThatThrownBy(() -> service.status(new JiraAppPrincipal("cloud-1", null, null)))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verifyNoInteractions(links);
    }

    @Test
    void startsALinkRequestWithTheFitsAccountTheJiraNameAndTheSiteHost_AC_014_6() {
        when(jiraAccounts.current()).thenReturn(Optional.of(JIRA));
        when(jiraUsers.displayName(JIRA, "jira-1")).thenReturn(Optional.of("An Nguyen"));
        when(requests.create(WORKSPACE, "jira-1", "An Nguyen", "acme.atlassian.net"))
                .thenReturn(URL);

        assertThat(service.start(CALLER)).isEqualTo(new JiraAccountLinkStart(URL));
    }

    @Test
    void takesTheSiteAndTheAccountOnlyFromTheFit_AC_014_10() {
        when(jiraAccounts.current()).thenReturn(Optional.of(JIRA));
        when(jiraUsers.displayName(JIRA, "jira-1")).thenReturn(Optional.of("An Nguyen"));
        when(requests.create(any(), anyString(), anyString(), anyString())).thenReturn(URL);

        service.start(CALLER);

        verify(workspaces).workspaceOf("cloud-1");
        verify(requests).create(WORKSPACE, "jira-1", "An Nguyen", "acme.atlassian.net");
    }

    @Test
    void refusesASiteOfNoWorkspaceWithMsg124_AC_014_9() {
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(CALLER)).isInstanceOf(JiraSiteNotConnectedException.class);
        verifyNoInteractions(jiraUsers, requests);
    }

    @Test
    void refusesAFitWithoutAJiraUserWithMsg08_AC_014_9() {
        assertThatThrownBy(() -> service.start(new JiraAppPrincipal("cloud-1", " ", null)))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verifyNoInteractions(jiraUsers, requests);
    }

    @Test
    void answers503Msg06WithoutAnAppToken_AC_014_9() {
        when(jiraAccounts.current()).thenReturn(Optional.empty());

        assertUnavailable();
        verifyNoInteractions(jiraUsers, requests);
    }

    @Test
    void answers503Msg06WithoutADisplayName_AC_014_9() {
        when(jiraAccounts.current()).thenReturn(Optional.of(JIRA));
        when(jiraUsers.displayName(JIRA, "jira-1")).thenReturn(Optional.empty());

        assertUnavailable();
        verifyNoInteractions(requests);
    }

    @Test
    void answers503Msg06WhenTheLinkRequestStoreFails_AC_014_9() {
        when(jiraAccounts.current()).thenReturn(Optional.of(JIRA));
        when(jiraUsers.displayName(JIRA, "jira-1")).thenReturn(Optional.of("An Nguyen"));
        when(requests.create(any(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.start(CALLER))
                .isInstanceOf(JiraAccountLinkUnavailableException.class)
                .satisfies(e -> assertThat(((MessageCodeException) e).getCode()).isEqualTo("MSG06"));
    }
}
