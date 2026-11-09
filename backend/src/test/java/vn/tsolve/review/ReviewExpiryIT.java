package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.review.repository.ReviewExpiryRepository;
import vn.tsolve.review.service.ReviewExpiryService;
import vn.tsolve.review.service.impl.ReviewExpiryServiceImpl;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.mapper.TicketReviewMapperImpl;
import vn.tsolve.ticket.service.TicketReviewReadService;
import vn.tsolve.ticket.service.impl.TicketReviewReadServiceImpl;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    ReviewExpiryRepository.class,
    ReviewExpiryServiceImpl.class,
    TicketReviewReadServiceImpl.class,
    TicketReviewMapperImpl.class,
    ReviewExpiryIT.JdbcConfiguration.class
})
class ReviewExpiryIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JdbcConfiguration {
        @Bean
        JdbcClient jdbcClient(JdbcTemplate jdbc) {
            return JdbcClient.create(jdbc);
        }
    }

    @Autowired
    private ReviewExpiryService expiry;

    @Autowired
    private TicketReviewReadService reads;

    @Autowired
    private JdbcTemplate jdbc;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private UUID firstProject;
    private UUID secondProject;
    private UUID inactiveProject;
    private UUID department;

    @BeforeEach
    void seed() {
        jdbc.update("delete from review_decision");
        jdbc.update("delete from evidence");
        jdbc.update("delete from ticket");
        long active = jdbc.queryForObject(
                "insert into department(name, status) values (?, 'ACTIVE') returning department_id",
                Long.class,
                UUID.randomUUID().toString());
        department =
                jdbc.queryForObject("select public_id from department where department_id = ?", UUID.class, active);
        // The current schema has no deleted-state enum; production readers allow only ACTIVE Departments.
        long inactive = jdbc.queryForObject(
                "insert into department(name, status) values (?, 'INACTIVE') returning department_id",
                Long.class,
                UUID.randomUUID().toString());
        firstProject = project(active);
        secondProject = project(active);
        inactiveProject = project(inactive);
    }

    @Test
    void expiresBothStatusesAndCountsEachProject_AC_014_3_AC_014_5() {
        UUID pending = ticket(firstProject, "PENDING_REVIEW", TODAY.minusDays(1));
        UUID changes = ticket(firstProject, "CHANGES_REQUESTED", TODAY.minusDays(2));
        UUID other = ticket(secondProject, "PENDING_REVIEW", TODAY.minusDays(3));
        var scope = Map.of(
                firstProject,
                new ProjectAccess(new Project(firstProject, "First"), department, true),
                secondProject,
                new ProjectAccess(new Project(secondProject, "Second"), department, true));
        assertThat(reads.eligibleIds(scope, null)).containsExactlyInAnyOrder(pending, other);
        assertThat(reads.queue(scope, null, PageRequest.of(0, 20), List.of()).getTotalElements())
                .isEqualTo(2);

        assertThat(expiry.expireOverdue(TODAY))
                .containsExactlyInAnyOrderEntriesOf(Map.of(firstProject, 2, secondProject, 1));
        for (UUID id : List.of(pending, changes, other)) {
            assertThat(row(id))
                    .containsEntry("status", "EXPIRED")
                    .containsEntry("status_before_expiry", id.equals(changes) ? "CHANGES_REQUESTED" : "PENDING_REVIEW")
                    .hasEntrySatisfying("expired_at", value -> assertThat(value).isNotNull());
        }
        assertThat(reads.eligibleIds(scope, null)).isEmpty();
        assertThat(reads.queue(scope, null, PageRequest.of(0, 20), List.of()).getContent())
                .isEmpty();
    }

    @Test
    void preservesExcludedTicketsContentAndHistoryAndIsIdempotent_AC_014_4_AC_014_5_AC_014_8() {
        UUID expired = ticket(firstProject, "PENDING_REVIEW", TODAY.minusDays(1));
        List<UUID> excluded = List.of(
                ticket(firstProject, "PENDING_REVIEW", TODAY),
                ticket(firstProject, "CHANGES_REQUESTED", TODAY.plusDays(1)),
                ticket(firstProject, "PENDING_REVIEW", null),
                ticket(firstProject, "PUBLISHED", TODAY.minusDays(1)),
                ticket(firstProject, "REJECTED", TODAY.minusDays(1)),
                ticket(firstProject, "UNPUBLISHED", TODAY.minusDays(1)),
                ticket(firstProject, "EXPIRED", TODAY.minusDays(1)),
                ticket(inactiveProject, "PENDING_REVIEW", TODAY.minusDays(1)));
        var beforeExcluded = excluded.stream().map(this::row).toList();
        jdbc.update(
                "insert into evidence(ticket_id, file_name, file_type, file_size) values (?, 'proof.png', 'image/png', 3)",
                expired);
        UUID reviewer = jdbc.queryForObject(
                "insert into app_user(email, status, created_at) values (?, 'ACTIVE', now()) returning id",
                UUID.class,
                UUID.randomUUID() + "@example.test");
        jdbc.update(
                "insert into review_decision(ticket_id, project_manager_id, decision, reason_or_comment) values (?, ?, 'CHANGES_REQUESTED', 'Earlier review')",
                expired,
                reviewer);
        var content = content(expired);
        var evidence = jdbc.queryForList("select * from evidence where ticket_id = ?", expired);
        var decisions = jdbc.queryForList("select * from review_decision order by review_decision_id");
        var audit = jdbc.queryForList("select * from audit_log_entry");
        expiry.expireOverdue(TODAY);
        assertThat(excluded.stream().map(this::row).toList()).isEqualTo(beforeExcluded);
        assertThat(content(expired)).isEqualTo(content);
        assertThat(jdbc.queryForList("select * from evidence where ticket_id = ?", expired))
                .isEqualTo(evidence);
        assertThat(jdbc.queryForList("select * from review_decision order by review_decision_id"))
                .isEqualTo(decisions);
        assertThat(jdbc.queryForList("select * from audit_log_entry")).isEqualTo(audit);
        var all = jdbc.queryForList("select * from ticket order by ticket_id");
        assertThat(expiry.expireOverdue(TODAY)).isEmpty();
        assertThat(jdbc.queryForList("select * from ticket order by ticket_id")).isEqualTo(all);
    }

    @Test
    void noOverdueTicketsChangesNothing_AC_014_5() {
        UUID id = ticket(firstProject, "PENDING_REVIEW", TODAY);
        var before = row(id);
        assertThat(expiry.expireOverdue(TODAY)).isEmpty();
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void statementFailureRollsBackEveryTicket_AC_014_6() {
        ticket(firstProject, "PENDING_REVIEW", TODAY.minusDays(1));
        UUID blocked = ticket(secondProject, "CHANGES_REQUESTED", TODAY.minusDays(1));
        var before = jdbc.queryForList("select * from ticket order by ticket_id");
        jdbc.execute("alter table ticket add constraint test_expiry_failure check (ticket_id <> '" + blocked
                + "'::uuid or status <> 'EXPIRED')");
        try {
            assertThatThrownBy(() -> expiry.expireOverdue(TODAY)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForList("select * from ticket order by ticket_id"))
                    .isEqualTo(before);
        } finally {
            jdbc.execute("alter table ticket drop constraint test_expiry_failure");
        }
    }

    @Test
    void nullableColumnsHaveTheRequiredTypesAndPreviousStatusConstraint_AC_014_8() {
        UUID id = ticket(firstProject, "PENDING_REVIEW", TODAY);
        assertThat(row(id)).containsEntry("expired_at", null).containsEntry("status_before_expiry", null);
        assertThat(
                        jdbc.queryForList(
                                "select column_name, data_type, is_nullable, character_maximum_length from information_schema.columns where table_schema = current_schema() and table_name = 'ticket' and column_name in ('expired_at', 'status_before_expiry') order by column_name"))
                .hasSize(2)
                .satisfies(columns -> {
                    assertThat(columns.get(0))
                            .containsEntry("data_type", "timestamp with time zone")
                            .containsEntry("is_nullable", "YES");
                    assertThat(columns.get(1))
                            .containsEntry("data_type", "character varying")
                            .containsEntry("is_nullable", "YES")
                            .containsEntry("character_maximum_length", 20);
                });
        assertThatThrownBy(() ->
                        jdbc.update("update ticket set status_before_expiry = 'PUBLISHED' where ticket_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID project(long departmentId) {
        return jdbc.queryForObject(
                "insert into project(department_id, jira_project_key) values (?, ?) returning public_id",
                UUID.class,
                departmentId,
                UUID.randomUUID().toString().substring(0, 18));
    }

    private UUID ticket(UUID project, String status, LocalDate due) {
        return jdbc.queryForObject(
                """
                insert into ticket(source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                   resolution_steps, workaround, scope, review_due_date)
                values (?, ?, 'SOLUTION_FORM', ?, 'Expiry test', 'Symptom', 'Cause', 'Restart', 'Workaround', 'Scope', ?)
                returning ticket_id
                """,
                UUID.class,
                UUID.randomUUID().toString(),
                project,
                status,
                due);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select * from ticket where ticket_id = ?", id);
    }

    private Map<String, Object> content(UUID id) {
        return jdbc.queryForMap(
                "select symptoms, root_cause, resolution_steps, workaround, scope, review_due_date, rejection_reason, change_request_comment, review_queue_entered_at from ticket where ticket_id = ?",
                id);
    }
}
