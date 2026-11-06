package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ReviewStorageIT {

    @Test
    void upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13() throws Exception {
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
                liquibase.update(1, new Contexts(), new LabelExpression());
                jdbc.update(
                        """
                        insert into ticket (source_ticket_id, project_id, source, status, title, symptoms,
                                            root_cause, resolution_steps, scope)
                        values ('TEST-LEGACY', 1, 'SOLUTION_FORM', 'PENDING_REVIEW', 'Old ticket', 'Error',
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
