package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;
import vn.tsolve.workspace.service.JiraAppTokenService;

class WorkspaceJiraServiceAccountTest {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String SITE = "https://example.atlassian.net";
    private static final String CLOUD_ID = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final String TOKEN = "obviously-fake-app-token";
    private static final Instant AT = Instant.parse("2026-11-17T03:00:00Z");

    private final TenantScope scope = mock(TenantScope.class);
    private final WorkspaceInfoRepository repository = mock(WorkspaceInfoRepository.class);
    private final JiraAppTokenService tokens = mock(JiraAppTokenService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AccountBeans.class)
            .withBean(TenantScope.class, () -> scope)
            .withBean(WorkspaceInfoRepository.class, () -> repository)
            .withBean(JiraAppTokenService.class, () -> tokens);

    @BeforeEach
    void bindWorkspace() {
        when(scope.currentWorkspaceId()).thenReturn(WORKSPACE);
    }

    private static JiraAppToken appToken(UUID unused, String cloudId, String token) {
        return new JiraAppToken(token, cloudId, AT, AT.plusSeconds(3600));
    }

    private void row(UUID workspaceId, String site, String cloudId) {
        when(repository.findById(workspaceId))
                .thenReturn(Optional.of(new WorkspaceInfo(workspaceId, "Example", site, cloudId)));
    }

    @Test
    @DisplayName("AC-008.1 returns the Workspace's site with the cloud id and app token held by STORY-014")
    void returnsSiteAndAppToken() {
        row(WORKSPACE, SITE, CLOUD_ID);
        when(tokens.current(WORKSPACE)).thenReturn(Optional.of(appToken(WORKSPACE, CLOUD_ID, TOKEN)));

        withAccount(account -> assertThat(account.current()).contains(new JiraAccount(SITE, CLOUD_ID, TOKEN)));
    }

    @Test
    @DisplayName("AC-008.1 returns empty before the Workspace is set up")
    void returnsEmptyWithoutRow() {
        when(repository.findById(WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> assertThat(account.current()).isEmpty());
    }

    @Test
    @DisplayName("AC-008.1 returns empty while no app token is held")
    void returnsEmptyWithoutToken() {
        row(WORKSPACE, SITE, CLOUD_ID);
        when(tokens.current(WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> assertThat(account.current()).isEmpty());
    }

    @Test
    @DisplayName("AC-008.4 never uses a token of another site and logs only the Workspace id")
    void refusesATokenOfAnotherSite() {
        row(WORKSPACE, SITE, CLOUD_ID);
        when(tokens.current(WORKSPACE))
                .thenReturn(Optional.of(appToken(WORKSPACE, "2324a887-45db-1bf4-1e99-ef0ff456d421", TOKEN)));
        Logger logger = (Logger) LoggerFactory.getLogger(WorkspaceJiraServiceAccount.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            withAccount(account -> assertThat(account.current()).isEmpty());
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains(WORKSPACE.toString())
                .doesNotContain(TOKEN));
    }

    @Test
    @DisplayName("AC-008.4 a row set up before KAN-344 (no cloud id) uses no token")
    void refusesATokenForARowWithoutCloudId() {
        row(WORKSPACE, SITE, null);
        when(tokens.current(WORKSPACE)).thenReturn(Optional.of(appToken(WORKSPACE, CLOUD_ID, TOKEN)));

        withAccount(account -> assertThat(account.current()).isEmpty());
    }

    @Test
    @DisplayName("AC-008.1 a worker job reads the Workspace bound by WorkerTenantService without touching TenantScope")
    void usesWorkerBinding() {
        WorkerTenantService worker = mock(WorkerTenantService.class);
        when(worker.currentWorkspaceId()).thenReturn(Optional.of(OTHER_WORKSPACE));
        row(OTHER_WORKSPACE, SITE, CLOUD_ID);
        when(tokens.current(OTHER_WORKSPACE)).thenReturn(Optional.of(appToken(OTHER_WORKSPACE, CLOUD_ID, TOKEN)));

        runner.withBean(WorkerTenantService.class, () -> worker).run(context -> assertThat(
                        context.getBean(JiraServiceAccount.class).current())
                .contains(new JiraAccount(SITE, CLOUD_ID, TOKEN)));

        verify(repository).findById(OTHER_WORKSPACE);
        verifyNoInteractions(scope);
    }

    @Test
    @DisplayName("AC-008.1 falls back to TenantScope when the worker binds no Workspace")
    void fallsBackWhenWorkerUnbound() {
        WorkerTenantService worker = mock(WorkerTenantService.class);
        when(worker.currentWorkspaceId()).thenReturn(Optional.empty());
        row(WORKSPACE, SITE, CLOUD_ID);
        when(tokens.current(WORKSPACE)).thenReturn(Optional.of(appToken(WORKSPACE, CLOUD_ID, TOKEN)));

        runner.withBean(WorkerTenantService.class, () -> worker).run(context -> assertThat(
                        context.getBean(JiraServiceAccount.class).current())
                .contains(new JiraAccount(SITE, CLOUD_ID, TOKEN)));

        verify(scope).currentWorkspaceId();
    }

    @Test
    @DisplayName("AC-008.2 the application discovers exactly one JiraServiceAccount bean owned by workspace")
    void discoversOnlyWorkspaceAccountBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JiraServiceAccount.class);
            assertThat(context.getBean(JiraServiceAccount.class).getClass().getName())
                    .isEqualTo("vn.tsolve.workspace.service.impl.WorkspaceJiraServiceAccount");
        });
    }

    private void withAccount(Consumer<JiraServiceAccount> check) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            check.accept(context.getBean(JiraServiceAccount.class));
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "vn.tsolve",
            useDefaultFilters = false,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JiraServiceAccount.class))
    static class AccountBeans {}
}
