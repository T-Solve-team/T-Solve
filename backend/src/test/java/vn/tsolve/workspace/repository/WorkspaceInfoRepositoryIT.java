package vn.tsolve.workspace.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.entity.WorkspaceInfo;

@SpringBootTest
@Import(TwoTenantDatabases.class)
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class WorkspaceInfoRepositoryIT {

    @Autowired
    private WorkspaceInfoRepository repository;

    @Autowired
    private TenantScope scope;

    @Autowired
    private TokenCipher cipher;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcTemplate routingJdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        TwoTenantDatabases.tenantA().update("DELETE FROM workspace");
        TwoTenantDatabases.tenantB().update("DELETE FROM workspace");
    }

    @Test
    @DisplayName("AC-003.1 stores the bound Workspace id and setup fields with the encrypted token")
    void storesTheSetupFields() {
        UUID id = TwoTenantDatabases.ACME;
        String encrypted = cipher.encrypt(id, "obviously-fake-jira-token");
        scope.runIn(
                id,
                () -> repository.saveAndFlush(new WorkspaceInfo(
                        id, "Fake company", "https://fake.atlassian.net", "fake@example.test", encrypted)));

        scope.runIn(id, () -> {
            assertThat(repository.findById(id)).hasValueSatisfying(workspace -> {
                assertThat(workspace.getId()).isEqualTo(id);
                assertThat(workspace.getCompanyName()).isEqualTo("Fake company");
                assertThat(workspace.getSignInProvider()).isEqualTo("GOOGLE");
                assertThat(workspace.getJiraSiteAddress()).isEqualTo("https://fake.atlassian.net");
                assertThat(workspace.getServiceAccountEmail()).isEqualTo("fake@example.test");
                assertThat(workspace.getServiceAccountApiToken()).isEqualTo(encrypted);
                assertThat(workspace.getTokenExpiryDate()).isNull();
            });
            return null;
        });
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT singleton FROM workspace", Boolean.class))
                .isTrue();
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("AC-003.11 singleton constraint refuses a second row even with a different Workspace id")
    void rejectsASecondRow() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        insertWorkspace(db, TwoTenantDatabases.ACME);

        assertThatThrownBy(() -> insertWorkspace(db, TwoTenantDatabases.BETA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_workspace_singleton");
        assertThat(db.queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-003.11 singleton cannot be bypassed with false or null")
    void rejectsFalseOrNullSingleton() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        for (Boolean singleton : new Boolean[] {false, null}) {
            assertThatThrownBy(() -> db.update(
                            "INSERT INTO workspace (workspace_id, company_name, sign_in_provider, singleton)"
                                    + " VALUES (?, 'Fake company', 'GOOGLE', ?)",
                            UUID.randomUUID(),
                            singleton))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("AC-003.1 schema uses the specified columns lengths nullability and explicit primary key")
    void matchesTheDataModel() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(db.queryForList("""
                        SELECT column_name || ':' || data_type || ':' || is_nullable || ':' ||
                               coalesce(character_maximum_length::text, '-')
                          FROM information_schema.columns
                         WHERE table_schema = 'public' AND table_name = 'workspace'
                        """, String.class))
                .containsExactlyInAnyOrder(
                        "workspace_id:uuid:NO:-", "company_name:character varying:NO:150",
                        "sign_in_provider:character varying:NO:30", "jira_site_address:character varying:YES:255",
                        "service_account_email:character varying:YES:254", "service_account_api_token:text:YES:-",
                        "token_expiry_date:date:YES:-", "singleton:boolean:NO:-");
        assertThat(db.queryForObject(
                        "SELECT conname FROM pg_constraint WHERE conrelid = 'workspace'::regclass AND contype = 'p'",
                        String.class))
                .isEqualTo("pk_workspace_info");
    }

    @Test
    @DisplayName("AC-003.1 legacy snapshot and solution foreign keys still reference legacy_workspace")
    void preservesLegacyForeignKeysAndRows() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertLegacyReferences(db, "legacy_workspace");
        assertThat(db.queryForObject("SELECT count(*) FROM legacy_workspace WHERE id BETWEEN 1 AND 4", Integer.class))
                .isEqualTo(4);

        scope.runIn(TwoTenantDatabases.ACME, () -> {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                long legacy = routingJdbc.queryForObject(
                        "INSERT INTO legacy_workspace (name, domain) VALUES ('Fake legacy', 'IT') RETURNING id",
                        Long.class);
                long snapshot = routingJdbc.queryForObject(
                        "INSERT INTO resolved_ticket_snapshot (workspace_id, source, external_id, title)"
                                + " VALUES (?, 'CSV', 'KAN-119-legacy-test', 'Fake snapshot') RETURNING id",
                        Long.class,
                        legacy);
                long solution = routingJdbc.queryForObject(
                        "INSERT INTO solution (workspace_id, title) VALUES (?, 'Fake solution') RETURNING id",
                        Long.class,
                        legacy);
                assertThat(routingJdbc.queryForObject(
                                "SELECT workspace_id FROM resolved_ticket_snapshot WHERE id = ?", Long.class, snapshot))
                        .isEqualTo(legacy);
                assertThat(routingJdbc.queryForObject(
                                "SELECT workspace_id FROM solution WHERE id = ?", Long.class, solution))
                        .isEqualTo(legacy);
                status.setRollbackOnly();
            });
            return null;
        });
    }

    @Test
    @DisplayName("AC-003.1 last changeset rolls back the new table and restores the legacy name and foreign keys")
    void rollsBackAndReappliesWorkspaceMigration() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantB();
        try (Connection connection = db.getDataSource().getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            var changesets = liquibase.getDatabaseChangeLog().getChangeSets();
            assertThat(changesets.getLast().getId()).isEqualTo("029-workspace-info");
            try {
                liquibase.rollback(1, "");
                assertThat(db.queryForObject("SELECT to_regclass('legacy_workspace')::text", String.class))
                        .isNull();
                assertThat(db.queryForObject("SELECT count(*) FROM workspace WHERE id BETWEEN 1 AND 4", Integer.class))
                        .isEqualTo(4);
                assertLegacyReferences(db, "workspace");
            } finally {
                liquibase.update("");
            }
        }
        assertLegacyReferences(db, "legacy_workspace");
        assertThat(db.queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    private static void insertWorkspace(JdbcTemplate db, UUID id) {
        db.update(
                "INSERT INTO workspace (workspace_id, company_name, sign_in_provider) VALUES (?, 'Fake company', 'GOOGLE')",
                id);
    }

    private static void assertLegacyReferences(JdbcTemplate db, String target) {
        assertThat(db.queryForList("""
                        SELECT conrelid::regclass::text FROM pg_constraint
                         WHERE contype = 'f' AND confrelid = to_regclass(?)
                           AND conrelid IN ('resolved_ticket_snapshot'::regclass, 'solution'::regclass)
                        """, String.class, target))
                .containsExactlyInAnyOrderElementsOf(List.of("resolved_ticket_snapshot", "solution"));
    }
}
