package vn.tsolve.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SearchSchemaIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final List<String> PUBLIC_ID_TABLES = List.of("ticket", "project", "department");
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private String databaseName;
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startServer() {
        SERVER.start();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop();
    }

    @BeforeEach
    void createDatabase() {
        databaseName = "search_" + UUID.randomUUID().toString().replace("-", "");
        adminJdbc().execute("CREATE DATABASE " + databaseName);
        dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + databaseName,
                SERVER.getUsername(),
                SERVER.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropDatabase() {
        adminJdbc().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
    }

    @Test
    void givesEveryExistingRowItsOwnPublicId_AC_005_1() throws Exception {
        migrateTo(dataSource, "007-identity-hand-off-code");
        insertTicketsAndProjects();

        migrateAll(dataSource);

        for (String table : PUBLIC_ID_TABLES) {
            assertThat(jdbc.queryForList("SELECT public_id FROM " + table, UUID.class))
                    .as("public ids on existing %s rows", table)
                    .hasSize(2)
                    .doesNotContainNull()
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void rejectsDuplicatePublicId_AC_005_1() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();

        for (String table : PUBLIC_ID_TABLES) {
            List<UUID> publicIds = jdbc.queryForList("SELECT public_id FROM " + table, UUID.class);
            assertThat(publicIds).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();

            assertThatThrownBy(() -> jdbc.update(
                            "UPDATE " + table + " SET public_id = ? WHERE public_id = ?",
                            publicIds.get(0),
                            publicIds.get(1)))
                    .as("public ids must be unique on %s", table)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_" + table + "_public_id");
        }
    }

    @Test
    void copiesCreatedAtIntoUpdatedAt_AC_005_1() throws Exception {
        migrateTo(dataSource, "008-search-public-ids");
        insertTicketsAndProjects();
        jdbc.update("UPDATE ticket SET created_at = '2026-01-01T00:00:00Z'::timestamptz");
        migrateAll(dataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket WHERE updated_at = created_at", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void setsUpdatedAtOnEveryUpdate_AC_005_1() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        var before = jdbc.queryForObject(
                "SELECT updated_at FROM ticket WHERE ticket_id = 'SEARCH-1'", java.sql.Timestamp.class);
        jdbc.update("UPDATE ticket SET title = 'Changed' WHERE ticket_id = 'SEARCH-1'");
        var after = jdbc.queryForObject(
                "SELECT updated_at FROM ticket WHERE ticket_id = 'SEARCH-1'", java.sql.Timestamp.class);
        assertThat(after).isAfter(before);
    }

    private void insertTicketsAndProjects() {
        for (int index = 1; index <= 2; index++) {
            Long departmentId = jdbc.queryForObject(
                    "INSERT INTO department (name) VALUES (?) RETURNING department_id",
                    Long.class,
                    "Department " + index);
            Long projectId = jdbc.queryForObject(
                    "INSERT INTO project (department_id, jira_project_key) VALUES (?, ?) RETURNING project_id",
                    Long.class,
                    departmentId,
                    "SEARCH" + index);
            jdbc.update(
                    """
                    INSERT INTO ticket (ticket_id, project_id, source, status, title,
                                        symptoms, root_cause, resolution_steps, scope)
                    VALUES (?, ?, 'CSV', 'PUBLISHED', ?, 'Symptoms', 'Root cause', 'Resolution', 'Scope')
                    """,
                    "SEARCH-" + index,
                    projectId,
                    "Ticket " + index);
        }
    }

    private static JdbcTemplate adminJdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword()));
    }

    private static void migrateTo(DataSource source, String lastChangesetId) throws Exception {
        try (Connection connection = source.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            List<ChangeSet> changesets = liquibase.getDatabaseChangeLog().getChangeSets();
            for (int index = 0; index < changesets.size(); index++) {
                if (lastChangesetId.equals(changesets.get(index).getId())) {
                    liquibase.update(index + 1, "");
                    return;
                }
            }
            throw new IllegalArgumentException("Unknown changeset: " + lastChangesetId);
        }
    }

    private static void migrateAll(DataSource source) throws Exception {
        try (Connection connection = source.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update("");
        }
    }
}
