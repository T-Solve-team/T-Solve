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
    private static final String EMAIL = "fake-bot@example.test";
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
    @DisplayName("AC-004.1 returns the bound Workspace's site, email and decrypted token")
    void returnsAccount() {
        store(SITE, EMAIL, cipher.encrypt(WORKSPACE, TOKEN));

        withAccount(account -> assertThat(account.current()).contains(new JiraAccount(SITE, EMAIL, TOKEN)));

        verify(repository).findById(WORKSPACE);
    }

    @Test
    @DisplayName("AC-004.1 returns empty before the bound Workspace is set up")
    void returnsEmptyWithoutRow() {
        when(repository.findById(WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> assertThat(account.current()).isEmpty());

        verify(repository).findById(WORKSPACE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("AC-004.1 returns empty when the stored site, email or token is missing")
    void returnsEmptyWithIncompleteAccount(String missing) {
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);
        withAccount(account -> {
            store(missing, EMAIL, encrypted);
            assertThat(account.current()).isEmpty();
            store(SITE, missing, encrypted);
            assertThat(account.current()).isEmpty();
            store(SITE, EMAIL, missing);
            assertThat(account.current()).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.1 reads the current Workspace and token again on every call")
    void followsBoundWorkspaceAndUpdates() {
        when(scope.currentWorkspaceId()).thenReturn(WORKSPACE, OTHER_WORKSPACE, WORKSPACE);
        WorkspaceInfo initial = row(SITE, EMAIL, cipher.encrypt(WORKSPACE, TOKEN));
        WorkspaceInfo updated = row(SITE, "updated@example.test", cipher.encrypt(WORKSPACE, "fake-updated-token"));
        when(repository.findById(WORKSPACE)).thenReturn(Optional.of(initial), Optional.of(updated));
        when(repository.findById(OTHER_WORKSPACE)).thenReturn(Optional.empty());

        withAccount(account -> {
            assertThat(account.current()).contains(new JiraAccount(SITE, EMAIL, TOKEN));
            assertThat(account.current()).isEmpty();
            assertThat(account.current()).contains(new JiraAccount(SITE, "updated@example.test", "fake-updated-token"));
        });
    }

    @Test
    @DisplayName("AC-004.1 a worker job reads the Workspace bound by WorkerTenantService without touching TenantScope")
    void usesWorkerBinding() {
        WorkerTenantService worker = mock(WorkerTenantService.class);
        when(worker.currentWorkspaceId()).thenReturn(Optional.of(OTHER_WORKSPACE));
        when(repository.findById(OTHER_WORKSPACE))
                .thenReturn(Optional.of(new WorkspaceInfo(
                        OTHER_WORKSPACE, "Other", SITE, EMAIL, cipher.encrypt(OTHER_WORKSPACE, TOKEN))));

        runner.withBean(WorkerTenantService.class, () -> worker).run(context -> assertThat(
                        context.getBean(JiraServiceAccount.class).current())
                .contains(new JiraAccount(SITE, EMAIL, TOKEN)));

        verify(repository).findById(OTHER_WORKSPACE);
        verifyNoInteractions(scope);
    }

    @Test
    @DisplayName("AC-004.1 falls back to TenantScope when the worker binds no Workspace")
    void fallsBackWhenWorkerUnbound() {
        WorkerTenantService worker = mock(WorkerTenantService.class);
        when(worker.currentWorkspaceId()).thenReturn(Optional.empty());
        store(SITE, EMAIL, cipher.encrypt(WORKSPACE, TOKEN));

        runner.withBean(WorkerTenantService.class, () -> worker).run(context -> assertThat(
                        context.getBean(JiraServiceAccount.class).current())
                .contains(new JiraAccount(SITE, EMAIL, TOKEN)));

        verify(scope).currentWorkspaceId();
        verify(repository).findById(WORKSPACE);
    }

    @Test
    @DisplayName("AC-004.1 falls back to TenantScope when no WorkerTenantService bean exists")
    void fallsBackWithoutWorkerBean() {
        store(SITE, EMAIL, cipher.encrypt(WORKSPACE, TOKEN));

        withAccount(account -> {
            assertThat(account.current()).contains(new JiraAccount(SITE, EMAIL, TOKEN));
        });

        verify(scope).currentWorkspaceId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tampered", "other-workspace", "wrong-key"})
    @DisplayName("AC-004.3 returns empty and logs an error without credentials or ciphertext on decrypt failure")
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
        store(SITE, EMAIL, encrypted);
        Logger logger =
                (Logger) LoggerFactory.getLogger("vn.tsolve.workspace.service.impl.WorkspaceJiraServiceAccount");
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            withAccount(account -> assertThat(account.current()).isEmpty());

            assertThat(logs.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).doesNotContain(TOKEN, EMAIL, encrypted);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    @DisplayName("AC-004.2 the application discovers exactly one JiraServiceAccount bean owned by workspace")
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

    private void store(String site, String email, String encrypted) {
        when(repository.findById(WORKSPACE)).thenReturn(Optional.of(row(site, email, encrypted)));
    }

    private static WorkspaceInfo row(String site, String email, String encrypted) {
        return new WorkspaceInfo(WORKSPACE, "Example", site, email, encrypted);
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
