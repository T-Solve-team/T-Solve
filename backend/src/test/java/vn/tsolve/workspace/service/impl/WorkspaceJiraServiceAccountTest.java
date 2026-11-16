package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.entity.WorkspaceInfo;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

class WorkspaceJiraServiceAccountTest {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String SITE = "https://example.atlassian.net";
    private static final String CLOUD_ID = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final String TOKEN = "obviously-fake-jira-token";

    private final TenantScope scope = mock(TenantScope.class);
    private final WorkspaceInfoRepository repository = mock(WorkspaceInfoRepository.class);
    private final TokenCipher cipher = cipher(new byte[32]);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AccountBeans.class)
            .withBean(TenantScope.class, () -> scope)
            .withBean(WorkspaceInfoRepository.class, () -> repository)
            .withBean(TokenCipher.class, () -> cipher);

    @BeforeEach
    void bindWorkspace() {
        when(scope.currentWorkspaceId()).thenReturn(WORKSPACE);
    }

    @Test
    @DisplayName("AC-008.1 returns the bound Workspace's site, cloud id and decrypted token")
    void returnsAccount() {
        store(SITE, CLOUD_ID, cipher.encrypt(WORKSPACE, TOKEN));

        withAccount(account -> assertThat(account.current()).contains(new JiraAccount(SITE, CLOUD_ID, TOKEN)));

        verify(repository).findById(WORKSPACE);
    }

    @Test
    @DisplayName("AC-008.1 returns empty before the bound Workspace is set up")
    void returnsEmptyWithoutRow() {
        when(repository.findById(WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> assertThat(account.current()).isEmpty());

        verify(repository).findById(WORKSPACE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("AC-008.1 AC-008.4 returns empty when the stored site, cloud id or token is missing")
    void returnsEmptyWithIncompleteAccount(String missing) {
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);
        withAccount(account -> {
            store(missing, CLOUD_ID, encrypted);
            assertThat(account.current()).isEmpty();
            store(SITE, missing, encrypted);
            assertThat(account.current()).isEmpty();
            store(SITE, CLOUD_ID, missing);
            assertThat(account.current()).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-008.1 reads the current Workspace and token again on every call")
    void followsBoundWorkspaceAndUpdates() {
        when(scope.currentWorkspaceId()).thenReturn(WORKSPACE, OTHER_WORKSPACE, WORKSPACE);
        WorkspaceInfo initial = row(SITE, CLOUD_ID, cipher.encrypt(WORKSPACE, TOKEN));
        WorkspaceInfo updated =
                row(SITE, "2324a887-45db-1bf4-1e99-ef0ff456d421", cipher.encrypt(WORKSPACE, "fake-updated-token"));
        when(repository.findById(WORKSPACE)).thenReturn(Optional.of(initial), Optional.of(updated));
        when(repository.findById(OTHER_WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> {
            assertThat(account.current()).contains(new JiraAccount(SITE, CLOUD_ID, TOKEN));
            assertThat(account.current()).isEmpty();
            assertThat(account.current())
                    .contains(new JiraAccount(SITE, "2324a887-45db-1bf4-1e99-ef0ff456d421", "fake-updated-token"));
        });
    }

    @Test
    @DisplayName("AC-008.1 a worker job reads the Workspace bound by WorkerTenantService without touching TenantScope")
    void usesWorkerBinding() {
        WorkerTenantService worker = mock(WorkerTenantService.class);
        when(worker.currentWorkspaceId()).thenReturn(Optional.of(OTHER_WORKSPACE));
        when(repository.findById(OTHER_WORKSPACE))
                .thenReturn(Optional.of(new WorkspaceInfo(
                        OTHER_WORKSPACE, "Other", SITE, CLOUD_ID, cipher.encrypt(OTHER_WORKSPACE, TOKEN))));

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
        store(SITE, CLOUD_ID, cipher.encrypt(WORKSPACE, TOKEN));

        runner.withBean(WorkerTenantService.class, () -> worker).run(context -> assertThat(
                        context.getBean(JiraServiceAccount.class).current())
                .contains(new JiraAccount(SITE, CLOUD_ID, TOKEN)));

        verify(scope).currentWorkspaceId();
        verify(repository).findById(WORKSPACE);
    }

    @Test
    @DisplayName("AC-008.1 falls back to TenantScope when no WorkerTenantService bean exists")
    void fallsBackWithoutWorkerBean() {
        store(SITE, CLOUD_ID, cipher.encrypt(WORKSPACE, TOKEN));

        withAccount(account -> {
            assertThat(account.current()).contains(new JiraAccount(SITE, CLOUD_ID, TOKEN));
        });

        verify(scope).currentWorkspaceId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tampered", "other-workspace", "wrong-key"})
    @DisplayName("AC-008.3 returns empty and logs an error without credentials or ciphertext on decrypt failure")
    void rejectsBrokenCiphertextSafely(String failure) {
        String encrypted;
        if (failure.equals("other-workspace")) {
            encrypted = cipher.encrypt(OTHER_WORKSPACE, TOKEN);
        } else if (failure.equals("wrong-key")) {
            byte[] otherKey = new byte[32];
            otherKey[0] = 1;
            encrypted = cipher(otherKey).encrypt(WORKSPACE, TOKEN);
        } else {
            byte[] packed =
                    Base64.getDecoder().decode(cipher.encrypt(WORKSPACE, TOKEN).substring(3));
            packed[packed.length - 1] ^= 1;
            encrypted = "v1:" + Base64.getEncoder().encodeToString(packed);
        }
        store(SITE, CLOUD_ID, encrypted);
        Logger logger =
                (Logger) LoggerFactory.getLogger("vn.tsolve.workspace.service.impl.WorkspaceJiraServiceAccount");
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            withAccount(account -> assertThat(account.current()).isEmpty());

            assertThat(logs.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).doesNotContain(TOKEN, CLOUD_ID, encrypted);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
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

    private void store(String site, String cloudId, String encrypted) {
        when(repository.findById(WORKSPACE)).thenReturn(Optional.of(row(site, cloudId, encrypted)));
    }

    private static WorkspaceInfo row(String site, String cloudId, String encrypted) {
        return new WorkspaceInfo(WORKSPACE, "Example", site, cloudId, encrypted);
    }

    private static TokenCipher cipher(byte[] key) {
        return new TokenCipher(new MockEnvironment()
                .withProperty("tsolve.workspace.secret-key", Base64.getEncoder().encodeToString(key)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "vn.tsolve",
            useDefaultFilters = false,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JiraServiceAccount.class))
    static class AccountBeans {}
}
