package vn.tsolve.platform.tenantdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.registry.PasswordCipher;
import vn.tsolve.platform.registry.RegistryRepository;
import vn.tsolve.platform.registry.RegistryRepository.WorkspaceDatabase;

/** The rules of the startup migration, with the Registry and the Liquibase seam as doubles. */
class TenantMigratorTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String OTHER_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    private static final String SERVER = "jdbc:postgresql://tenant-db.test:6543/";

    private final RegistryRepository registry = mock(RegistryRepository.class);
    private final TenantMigrator.ChangelogRunner runner = mock(TenantMigrator.ChangelogRunner.class);
    private final PasswordCipher cipher = cipher(KEY);
    private final TenantMigrator migrator = new TenantMigrator(registry, cipher, "tenant-db.test", 6543, runner);

    @Test
    @DisplayName("AC-008.4 every row, ACTIVE and SUSPENDED, is migrated as its db_user on the tenant server")
    void migratesActiveAndSuspendedRows_AC_008_4() {
        var alpha = row(cipher, "alpha", WorkspaceStatus.ACTIVE);
        var bravo = row(cipher, "bravo", WorkspaceStatus.SUSPENDED);
        when(registry.findAllConnections()).thenReturn(List.of(alpha, bravo));
        when(runner.apply(anyString(), anyString(), anyString())).thenReturn("last-id");

        migrator.applyAll();

        verify(runner).apply(SERVER + "db_alpha", "user_alpha", "fake-password-alpha");
        verify(runner).apply(SERVER + "db_bravo", "user_bravo", "fake-password-bravo");
        verifyNoMoreInteractions(runner);
    }

    @Test
    @DisplayName("AC-001.15 schema_version gets the last changeset id of each database, after that database's run")
    void writesLastChangesetIdAfterEachSuccess_AC_001_15() {
        var alpha = row(cipher, "alpha", WorkspaceStatus.ACTIVE);
        var bravo = row(cipher, "bravo", WorkspaceStatus.ACTIVE);
        when(registry.findAllConnections()).thenReturn(List.of(alpha, bravo));
        when(runner.apply(eq(SERVER + "db_alpha"), anyString(), anyString())).thenReturn("id-alpha");
        when(runner.apply(eq(SERVER + "db_bravo"), anyString(), anyString())).thenReturn("id-bravo");

        migrator.applyAll();

        var order = inOrder(runner, registry);
        order.verify(runner).apply(eq(SERVER + "db_alpha"), anyString(), anyString());
        order.verify(registry).updateSchemaVersion(alpha.workspaceId(), "id-alpha");
        order.verify(runner).apply(eq(SERVER + "db_bravo"), anyString(), anyString());
        order.verify(registry).updateSchemaVersion(bravo.workspaceId(), "id-bravo");
        verify(registry).updateSchemaVersion(eq(alpha.workspaceId()), anyString());
        verify(registry).updateSchemaVersion(eq(bravo.workspaceId()), anyString());
    }

    @Test
    @DisplayName("AC-008.9 edge 44 the first failure names the site, stops the run and leaves the other rows alone")
    void stopsAtTheFailingWorkspaceAndNamesIt_AC_008_9_edge_44() {
        var alpha = row(cipher, "alpha", WorkspaceStatus.ACTIVE);
        var bravo = row(cipher, "bravo", WorkspaceStatus.ACTIVE);
        var charlie = row(cipher, "charlie", WorkspaceStatus.ACTIVE);
        when(registry.findAllConnections()).thenReturn(List.of(alpha, bravo, charlie));
        when(runner.apply(eq(SERVER + "db_alpha"), anyString(), anyString())).thenReturn("id-alpha");
        when(runner.apply(eq(SERVER + "db_bravo"), anyString(), anyString()))
                .thenThrow(new IllegalStateException("changeset failed"));

        Throwable failure = catchThrowable(migrator::applyAll);

        assertThat(failure).isInstanceOf(RuntimeException.class).hasMessageContaining("bravo");
        assertThat(stackTrace(failure))
                .doesNotContain("fake-password-bravo", bravo.dbPassword())
                .doesNotContain(KEY);
        verify(runner, never()).apply(eq(SERVER + "db_charlie"), anyString(), anyString());
        verify(registry).updateSchemaVersion(alpha.workspaceId(), "id-alpha");
        verify(registry, never()).updateSchemaVersion(eq(bravo.workspaceId()), any());
        verify(registry, never()).updateSchemaVersion(eq(charlie.workspaceId()), any());
    }

    @Test
    @DisplayName("edge 44 a password stored under another key stops the run naming the site, without the secret")
    void refusesAnUndecryptablePasswordNamingTheSite_edge_44() {
        var alpha = row(cipher, "alpha", WorkspaceStatus.ACTIVE);
        var bravo = row(cipher(OTHER_KEY), "bravo", WorkspaceStatus.ACTIVE);
        when(registry.findAllConnections()).thenReturn(List.of(alpha, bravo));
        when(runner.apply(anyString(), anyString(), anyString())).thenReturn("id-alpha");

        Throwable failure = catchThrowable(migrator::applyAll);

        assertThat(failure).isInstanceOf(RuntimeException.class).hasMessageContaining("bravo");
        assertThat(stackTrace(failure))
                .doesNotContain(bravo.dbPassword(), "fake-password-bravo")
                .doesNotContain(KEY, OTHER_KEY);
        verify(runner, never()).apply(eq(SERVER + "db_bravo"), anyString(), anyString());
        verify(registry, never()).updateSchemaVersion(eq(bravo.workspaceId()), any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "db_bravo?sslmode=disable",
                "db_bravo/postgres",
                "db_bravo&user=postgres",
                "db_bravo%3Fsslmode=disable"
            })
    @DisplayName("AC-008.9 a db_name that is not a plain database name is refused before any connection")
    void refusesAnUnsafeDatabaseNameBeforeConnecting_AC_008_9(String dbName) {
        var good = row(cipher, "bravo", WorkspaceStatus.ACTIVE);
        var bravo = new WorkspaceDatabase(
                good.workspaceId(), "bravo", good.status(), dbName, good.dbUser(), good.dbPassword());
        when(registry.findAllConnections()).thenReturn(List.of(bravo));

        Throwable failure = catchThrowable(migrator::applyAll);

        assertThat(failure).isInstanceOf(RuntimeException.class).hasMessageContaining("bravo");
        assertThat(stackTrace(failure))
                .doesNotContain("fake-password-bravo", bravo.dbPassword())
                .doesNotContain(KEY);
        verifyNoInteractions(runner);
        verify(registry, never()).updateSchemaVersion(any(), any());
    }

    @Test
    @DisplayName("AC-008.4 an empty Registry migrates nothing and does not fail")
    void doesNothingWhenTheRegistryIsEmpty_AC_008_4() {
        when(registry.findAllConnections()).thenReturn(List.of());

        migrator.applyAll();

        verifyNoInteractions(runner);
        verify(registry, never()).updateSchemaVersion(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("AC-008.5 a blank TENANT_DB_HOST is refused, naming the variable")
    void refusesABlankTenantDbHost_AC_008_5(String host) {
        assertThatThrownBy(() -> new TenantMigrator(registry, cipher, host, 6543, runner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_DB_HOST");
        verifyNoInteractions(registry, runner);
    }

    private static WorkspaceDatabase row(PasswordCipher encryptedWith, String site, WorkspaceStatus status) {
        UUID id = UUID.randomUUID();
        return new WorkspaceDatabase(
                id, site, status, "db_" + site, "user_" + site, encryptedWith.encrypt(id, "fake-password-" + site));
    }

    private static PasswordCipher cipher(String key) {
        return new PasswordCipher(new MockEnvironment().withProperty("tsolve.registry.secret-key", key));
    }

    private static String stackTrace(Throwable failure) {
        var text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
