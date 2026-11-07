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
                "SELECT updated_at FROM ticket WHERE source_ticket_id = 'SEARCH-1'", java.sql.Timestamp.class);
        jdbc.update("UPDATE ticket SET title = 'Changed' WHERE source_ticket_id = 'SEARCH-1'");
        var after = jdbc.queryForObject(
                "SELECT updated_at FROM ticket WHERE source_ticket_id = 'SEARCH-1'", java.sql.Timestamp.class);
        assertThat(after).isAfter(before);
    }

    @Test
    void createsTicketEmbeddingWithTenantConvention_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        insertEmbedding("SEARCH-1", vector(1024));
        assertThat(jdbc.queryForObject("SELECT ticket_embedding_id FROM ticket_embedding", UUID.class))
                .isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ticket_embedding WHERE created_at IS NOT NULL AND updated_at IS NOT NULL",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket_embedding'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "ticket_embedding_id", "ticket_id", "embedding", "model", "created_at", "updated_at");
        assertThat(jdbc.queryForList(
                        """
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'ticket_embedding' AND column_name IN ('created_at', 'updated_at')
                          AND is_nullable = 'NO' AND column_default = 'now()'
                        """,
                        String.class))
                .containsExactly("timestamp with time zone", "timestamp with time zone");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO ticket_embedding (ticket_id, embedding, model) VALUES (?, CAST(? AS vector),"
                                + " 'bge-m3')",
                        missingTicketId(),
                        vector(1024)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM ticket WHERE " + sourceColumn() + " = 'SEARCH-1'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_embedding", Integer.class))
                .isZero();
    }

    @Test
    void refusesVectorOfOtherLength_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        assertThatThrownBy(() -> insertEmbedding("SEARCH-1", vector(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsOneEmbeddingPerTicket_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        insertEmbedding("SEARCH-1", vector(1024));
        assertThatThrownBy(() -> insertEmbedding("SEARCH-1", vector(1024)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ticket_embedding_ticket");
    }

    @Test
    void createsSearchLogWithTenantConvention_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Long departmentId = jdbc.queryForObject("SELECT min(department_id) FROM department", Long.class);

        UUID webLogId = insertSearchLog(userId, null, "WEB", 0, false);
        UUID panelLogId = insertSearchLog(userId, departmentId, "ISSUE_PANEL", 2, true);
        UUID chatbotLogId = insertSearchLog(userId, null, "CHATBOT", 1, false);

        assertThat(List.of(webLogId, panelLogId, chatbotLogId)).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_log WHERE created_at IS NOT NULL", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT is_fallback FROM search_log WHERE search_log_id = ?", Boolean.class, panelLogId))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT is_fallback FROM search_log WHERE search_log_id = ?", Boolean.class, webLogId))
                .isFalse();
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'search_log'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "search_log_id",
                        "user_id",
                        "department_id",
                        "query",
                        "channel",
                        "result_count",
                        "is_fallback",
                        "created_at");
        assertThat(jdbc.queryForList(
                        """
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'search_log' AND is_nullable = 'YES'
                        """,
                        String.class))
                .containsExactly("department_id");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'search_log' AND column_name = 'created_at'
                          AND is_nullable = 'NO' AND column_default = 'now()'
                        """,
                        String.class))
                .isEqualTo("timestamp with time zone");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT indexdef FROM pg_indexes
                        WHERE schemaname = 'public' AND tablename = 'search_log'
                          AND indexname = 'idx_search_log_created_at'
                        """,
                        String.class))
                .endsWith("USING btree (created_at)");
    }

    @Test
    void refusesUnknownChannel_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Object ticketId = ticketId("SEARCH-1");

        assertThatThrownBy(() -> insertSearchLog(userId, null, "UNKNOWN", 0, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_search_log_channel");
        for (String channel : List.of("UNKNOWN", "CHATBOT")) {
            assertThatThrownBy(() -> insertFeedback(ticketId, userId, "HELPFUL", null, channel))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_ticket_feedback_channel");
        }
    }

    @Test
    void refusesNegativeResultCount_AC_005_2() throws Exception {
        migrateAll(dataSource);
        UUID userId = insertUser("search@example.test");

        assertThatThrownBy(() -> insertSearchLog(userId, null, "WEB", -1, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_search_log_result_count");
    }

    @Test
    void enforcesSearchLogReferences_AC_005_2() throws Exception {
        migrateAll(dataSource);
        UUID userId = insertUser("search@example.test");

        assertThatThrownBy(() -> insertSearchLog(UUID.randomUUID(), null, "WEB", 0, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("search_log_user_id_fkey");
        assertThatThrownBy(() -> insertSearchLog(userId, -1L, "WEB", 0, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("search_log_department_id_fkey");
    }

    @Test
    void createsTicketFeedbackWithTenantConvention_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID firstUserId = insertUser("first@example.test");
        UUID secondUserId = insertUser("second@example.test");
        Object firstTicketId = ticketId("SEARCH-1");
        Object secondTicketId = ticketId("SEARCH-2");

        UUID firstFeedbackId = insertFeedback(firstTicketId, firstUserId, "HELPFUL", null, "WEB");
        UUID secondFeedbackId =
                insertFeedback(firstTicketId, secondUserId, "NOT_HELPFUL", "Needs detail", "ISSUE_PANEL");
        UUID thirdFeedbackId = insertFeedback(secondTicketId, firstUserId, "HELPFUL", null, "WEB");

        assertThat(List.of(firstFeedbackId, secondFeedbackId, thirdFeedbackId)).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ticket_feedback WHERE created_at IS NOT NULL AND updated_at IS NOT NULL",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket_feedback'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "ticket_feedback_id",
                        "ticket_id",
                        "user_id",
                        "rating",
                        "comment",
                        "channel",
                        "created_at",
                        "updated_at");
        assertThat(jdbc.queryForList(
                        """
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'ticket_feedback' AND is_nullable = 'YES'
                        """,
                        String.class))
                .containsExactly("comment");
        assertThat(jdbc.queryForList(
                        """
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'ticket_feedback' AND column_name IN ('created_at', 'updated_at')
                          AND is_nullable = 'NO' AND column_default = 'now()'
                        """,
                        String.class))
                .containsExactly("timestamp with time zone", "timestamp with time zone");
    }

    @Test
    void refusesSecondFeedbackOfSameUser_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Object ticketId = ticketId("SEARCH-1");
        insertFeedback(ticketId, userId, "HELPFUL", null, "WEB");

        assertThatThrownBy(() -> insertFeedback(ticketId, userId, "NOT_HELPFUL", "Changed", "ISSUE_PANEL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ticket_feedback_ticket_user");
    }

    @Test
    void refusesCommentOver500_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Object ticketId = ticketId("SEARCH-1");

        assertThatThrownBy(() -> insertFeedback(ticketId, userId, "HELPFUL", "a".repeat(501), "WEB"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ticket_feedback_comment");
        insertFeedback(ticketId, userId, "HELPFUL", "a".repeat(500), "WEB");
        assertThat(jdbc.queryForObject("SELECT char_length(comment) FROM ticket_feedback", Integer.class))
                .isEqualTo(500);
    }

    @Test
    void refusesUnknownRating_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Object ticketId = ticketId("SEARCH-1");

        assertThatThrownBy(() -> insertFeedback(ticketId, userId, "UNKNOWN", null, "WEB"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ticket_feedback_rating");
    }

    @Test
    void enforcesTicketFeedbackReferences_AC_005_2() throws Exception {
        migrateAll(dataSource);
        insertTicketsAndProjects();
        UUID userId = insertUser("search@example.test");
        Object ticketId = ticketId("SEARCH-1");

        assertThatThrownBy(() -> insertFeedback(missingTicketId(), userId, "HELPFUL", null, "WEB"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ticket_feedback_ticket_id_fkey");
        assertThatThrownBy(() -> insertFeedback(ticketId, UUID.randomUUID(), "HELPFUL", null, "WEB"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ticket_feedback_user_id_fkey");
    }

    private UUID insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
    }

    // Before Review the key is the numeric id and the source id is ticket_id; afterwards ticket_id is the UUID key.
    private boolean canonical() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from information_schema.columns where table_name='ticket'"
                        + " and column_name='source_ticket_id')",
                Boolean.class));
    }

    private String sourceColumn() {
        return canonical() ? "source_ticket_id" : "ticket_id";
    }

    // 010-ticket-import-project-uuid (KAN-31) runs before the Search changesets, so project_id is a UUID there too.
    private boolean projectIdIsUuid() {
        return "uuid"
                .equals(jdbc.queryForObject(
                        "select data_type from information_schema.columns where table_name='ticket'"
                                + " and column_name='project_id'",
                        String.class));
    }

    private Object missingTicketId() {
        return canonical() ? UUID.randomUUID() : -1L;
    }

    private Object ticketId(String sourceId) {
        return jdbc.queryForObject(
                "SELECT " + (canonical() ? "ticket_id" : "id") + " FROM ticket WHERE " + sourceColumn() + " = ?",
                Object.class,
                sourceId);
    }

    private UUID insertSearchLog(UUID userId, Long departmentId, String channel, int resultCount, boolean isFallback) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_log (user_id, department_id, query, channel, result_count, is_fallback)
                VALUES (?, ?, 'Search query', ?, ?, ?) RETURNING search_log_id
                """,
                UUID.class,
                userId,
                departmentId,
                channel,
                resultCount,
                isFallback);
    }

    private UUID insertFeedback(Object ticketId, UUID userId, String rating, String comment, String channel) {
        return jdbc.queryForObject(
                """
                INSERT INTO ticket_feedback (ticket_id, user_id, rating, comment, channel)
                VALUES (?, ?, ?, ?, ?) RETURNING ticket_feedback_id
                """,
                UUID.class,
                ticketId,
                userId,
                rating,
                comment,
                channel);
    }

    private void insertEmbedding(String sourceId, String vector) {
        jdbc.update(
                "INSERT INTO ticket_embedding (ticket_id, embedding, model) SELECT "
                        + (canonical() ? "ticket_id" : "id")
                        + ", CAST(? AS vector), 'bge-m3' FROM ticket WHERE "
                        + sourceColumn()
                        + " = ?",
                vector,
                sourceId);
    }

    private static String vector(int dimensions) {
        return "[" + String.join(",", java.util.Collections.nCopies(dimensions, "0")) + "]";
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
            boolean canonical = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from information_schema.columns where table_name='ticket' and column_name='source_ticket_id')",
                    Boolean.class));
            jdbc.update(
                    """
                    INSERT INTO ticket (%s, project_id, source, status, title,
                                        symptoms, root_cause, resolution_steps, scope)
                    VALUES (?, ?, 'CSV', 'PUBLISHED', ?, 'Symptoms', 'Root cause', 'Resolution', 'Scope')
                    """
                            .formatted(canonical ? "source_ticket_id" : "ticket_id"),
                    "SEARCH-" + index,
                    projectIdIsUuid() ? new UUID(0, projectId) : projectId,
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
