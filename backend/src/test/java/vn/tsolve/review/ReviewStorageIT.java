package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ReviewStorageIT {

    @Test
    void upgradesAlreadyRunningSearchAndPreservesItsTimestamp_BUG_144() throws Exception {
        try (var postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var source =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            try (var liquibase = new Liquibase(
                    "db/changelog/search-only.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(source.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            var jdbc = new JdbcTemplate(source);
            jdbc.update(
                    """
                    insert into ticket(ticket_id, project_id, source, status, title, symptoms, root_cause,
                                       resolution_steps, scope, updated_at)
                    values ('SEARCH-OLD', '00000000-0000-0000-0000-000000000001', 'CSV', 'PUBLISHED', 'Old Search', 's', 'r', 'st', 'sc', '2026-11-01T00:00:00Z')
                    """);
            var before = jdbc.queryForObject("select updated_at from ticket", OffsetDateTime.class);
            var publicId = jdbc.queryForObject("select public_id from ticket", UUID.class);
            try (var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(source.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            assertThat(jdbc.queryForObject("select updated_at from ticket", OffsetDateTime.class))
                    .isEqualTo(before);
            assertThat(jdbc.queryForObject("select public_id from ticket", UUID.class))
                    .isEqualTo(publicId);
            assertThat(jdbc.queryForObject("select project_id from ticket", UUID.class))
                    .isEqualTo(new UUID(0, 1));
        }
    }

    @Test
    void movesSearchEmbeddingAndFeedbackToTheUuidTicketKey_BUG_145(@TempDir Path temporary) throws Exception {
        String master;
        try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/db.changelog-master.xml")) {
            master = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // The changelog of main before Review: every changeset except Review's own.
        master = master.replaceAll(
                "(?m)^.*<include file=\"changes/(?:0\\d\\d-review-search|008-review|009-review).*\n", "");
        Path mainMaster = temporary.resolve("db/changelog/main-before-review.xml");
        Files.createDirectories(mainMaster.getParent());
        Files.writeString(mainMaster, master);
        try (var postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            try (var resources = new CompositeResourceAccessor(
                            new DirectoryResourceAccessor(temporary), new ClassLoaderResourceAccessor());
                    var liquibase = new Liquibase(
                            "db/changelog/main-before-review.xml",
                            resources,
                            new JdbcConnection(dataSource.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            jdbc.update(
                    """
                    insert into ticket(ticket_id, project_id, source, status, title, symptoms, root_cause,
                                       resolution_steps, scope)
                    values ('SEARCH-1', '00000000-0000-0000-0000-000000000001', 'CSV', 'PUBLISHED', 'Published',
                            's', 'r', 'st', 'sc')
                    """);
            UUID user = jdbc.queryForObject(
                    "insert into app_user(email, status, created_at) values ('pm@company.vn', 'ACTIVE', now())"
                            + " returning id",
                    UUID.class);
            jdbc.update("insert into ticket_embedding(ticket_id, embedding, model)"
                    + " select id, array_fill(0.1::real, ARRAY[1024])::vector, 'bge-m3' from ticket");
            jdbc.update(
                    "insert into ticket_feedback(ticket_id, user_id, rating, channel)"
                            + " select id, ?, 'HELPFUL', 'WEB' from ticket",
                    user);
            var publicId = jdbc.queryForObject("select public_id from ticket", UUID.class);

            try (var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(dataSource.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }

            UUID ticket =
                    jdbc.queryForObject("select ticket_id from ticket where source_ticket_id = 'SEARCH-1'", UUID.class);
            assertThat(jdbc.queryForObject("select ticket_id from ticket_embedding", UUID.class))
                    .isEqualTo(ticket);
            assertThat(jdbc.queryForObject("select ticket_id from ticket_feedback where user_id = ?", UUID.class, user))
                    .isEqualTo(ticket);
            assertThat(jdbc.queryForObject("select public_id from ticket", UUID.class))
                    .isEqualTo(publicId);
            assertThat(jdbc.queryForObject(
                            "select count(*) from information_schema.columns where table_name = 'ticket'"
                                    + " and column_name in ('id', 'review_legacy_id')",
                            Integer.class))
                    .isZero();
        }
    }

    @Test
    void upgradesADatabaseAlreadyRunningReviewWithoutReapplyingStorage_BUG_140(@TempDir Path temporary)
            throws Exception {
        String master;
        try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/db.changelog-master.xml")) {
            master = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(
                            "    <include file=\"changes/010-ticket-import-project-uuid.sql\" relativeToChangelogFile=\"true\"/>",
                            "");
        }
        master = master.replaceAll(
                "(?m)^.*<include file=\"changes/(?:0\\d\\d-search|0\\d\\d-review-search|013-chatbot|011-ticket-import"
                        + "|017-project-membership|0\\d\\d-review-queue-notify).*\n",
                "");
        Path oldMaster = temporary.resolve("db/changelog/review-before-project-uuid.xml");
        Files.createDirectories(oldMaster.getParent());
        Files.writeString(oldMaster, master);
        try (var postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            try (var resources = new CompositeResourceAccessor(
                            new DirectoryResourceAccessor(temporary), new ClassLoaderResourceAccessor());
                    var liquibase = new Liquibase(
                            "db/changelog/review-before-project-uuid.xml",
                            resources,
                            new JdbcConnection(dataSource.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            UUID id = jdbc.queryForObject(
                    """
                    insert into ticket (source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                        resolution_steps, scope)
                    values ('EXISTING-1', 10, 'SOLUTION_FORM', 'PENDING_REVIEW', 'Old review', 's', 'r', 'st', 'sc')
                    returning ticket_id
                    """,
                    UUID.class);
            try (var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(dataSource.getConnection()))) {
                liquibase.update(new Contexts(), new LabelExpression());
                liquibase.update(new Contexts(), new LabelExpression());
            }
            assertThat(jdbc.queryForObject("select project_id from ticket where ticket_id = ?", UUID.class, id))
                    .isEqualTo(new UUID(0, 10));
            assertThat(jdbc.queryForObject(
                            "select count(*) from databasechangelog where id = '008-review-ticket-storage'",
                            Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select count(*) from information_schema.columns where table_name = 'ticket'"
                                    + " and column_name in ('id', 'review_legacy_id')",
                            Integer.class))
                    .isZero();
        }
    }

    @Test
    void upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140() throws Exception {
        try (var postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            try (var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(dataSource.getConnection()))) {
                liquibase.update(7, new Contexts(), new LabelExpression());
                long oldId = jdbc.queryForObject(
                        """
                        insert into ticket (ticket_id, project_id, source, status, title, symptoms, root_cause,
                                            resolution_steps, scope)
                        values ('TEST-1', 1, 'CSV', 'PUBLISHED', 'Login fails', 'Error', 'Token', 'Restart', 'Users')
                        returning id
                        """,
                        Long.class);
                jdbc.update(
                        "insert into ticket_import_decision (ticket_id, reviewer, decision) values (?, 'legacy-pm', 'APPROVE')",
                        oldId);
                var pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
                int count = 0;
                for (var change : pending) {
                    count++;
                    if (change.getId().equals("008-review-ticket-storage")) break;
                }
                liquibase.update(count, new Contexts(), new LabelExpression());
                jdbc.update(
                        """
                        insert into ticket (source_ticket_id, project_id, source, status, title, symptoms,
                                            root_cause, resolution_steps, scope)
                        values ('TEST-LEGACY', '00000000-0000-0000-0000-000000000001', 'SOLUTION_FORM', 'PENDING_REVIEW', 'Old ticket', 'Error',
                                'Token', 'Restart', 'Users')
                        """);
                liquibase.update(new Contexts(), new LabelExpression());
            }

            assertThat(jdbc.queryForObject(
                            "select review_queue_entered_at from ticket where source_ticket_id = 'TEST-LEGACY'",
                            OffsetDateTime.class))
                    .isNull();

            UUID ticketId = jdbc.queryForObject(
                    "select ticket_id from ticket where source_ticket_id = 'TEST-1' and resolution_steps = 'Restart'",
                    UUID.class);
            assertThat(ticketId).isNotNull();
            assertThat(jdbc.queryForObject("select project_id from ticket where ticket_id = ?", UUID.class, ticketId))
                    .isEqualTo(new UUID(0, 1));
            assertThat(jdbc.queryForObject(
                            "select ticket_id from ticket_import_decision where reviewer = 'legacy-pm'", UUID.class))
                    .isEqualTo(ticketId);

            UUID reviewerId = jdbc.queryForObject(
                    "insert into app_user (email, status, created_at) values ('pm@example.test', 'ACTIVE', now()) returning id",
                    UUID.class);
            assertThat(jdbc.queryForObject(
                            """
                            insert into review_decision (ticket_id, project_manager_id, decision)
                            values (?, ?, 'APPROVED') returning review_decision_id
                            """,
                            UUID.class,
                            ticketId,
                            reviewerId))
                    .isNotNull();
            assertThat(jdbc.queryForObject(
                            """
                            insert into evidence (ticket_id, file_name, file_type, file_size)
                            values (?, 'error.png', 'image/png', 123) returning evidence_id
                            """,
                            UUID.class,
                            ticketId))
                    .isNotNull();
        }
    }
}
