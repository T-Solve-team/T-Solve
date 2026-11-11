package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.testsupport.SharedPostgres;
import vn.tsolve.ticket.config.TicketClockConfig;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.ReviewSourceNotEligibleException;
import vn.tsolve.ticket.service.TicketDecisionService;
import vn.tsolve.ticket.service.TicketDecisionService.Action;
import vn.tsolve.ticket.service.impl.TicketDecisionServiceImpl;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TicketClockConfig.class, TicketDecisionServiceImpl.class})
class TicketDecisionIT {
    @org.springframework.boot.test.mock.mockito.MockBean
    private vn.tsolve.common.audit.AuditService audit;

    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    @Autowired
    private TicketDecisionService service;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID reviewerId;

    @BeforeEach
    void seedReviewer() {
        jdbc.update("delete from review_decision");
        jdbc.update("delete from evidence");
        jdbc.update("delete from ticket");
        jdbc.update("delete from app_user");
        reviewerId = jdbc.queryForObject(
                "insert into app_user (email, status, created_at) values ('pm@example.test', 'ACTIVE', now()) returning id",
                UUID.class);
    }

    @Test
    void recordsNewQueueEntriesButPreservesTheTimeDuringEdits_AC_009_11() {
        UUID id = ticket("TEST-1", 10, "PENDING_REVIEW", null);
        OffsetDateTime firstEntry = queueEntryTime(id);
        assertThat(firstEntry).isNotNull();

        jdbc.update("update ticket set title = 'Updated title', updated_at = now() where ticket_id = ?", id);
        jdbc.update("update ticket set status = 'PENDING_REVIEW' where ticket_id = ?", id);
        assertThat(queueEntryTime(id)).isEqualTo(firstEntry);

        service.decide(
                reviewerId, Set.of(new UUID(0, 10)), List.of(id), Action.CHANGES_REQUESTED, "Explain the workaround");
        assertThat(queueEntryTime(id)).isEqualTo(firstEntry);
        jdbc.update("update ticket set status = 'PENDING_REVIEW' where ticket_id = ?", id);
        assertThat(queueEntryTime(id)).isAfter(firstEntry);
        assertThat(jdbc.queryForObject("select review_due_date from ticket where ticket_id = ?", LocalDate.class, id))
                .isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void commitsDecisionsAndPreservesTicketContent_AC_009_4_AC_009_5_AC_009_6_AC_009_8(Action action) {
        UUID first = ticket("TEST-1", 10, "PENDING_REVIEW", reviewerId);
        UUID second = ticket("TEST-2", 10, "PENDING_REVIEW", null);
        jdbc.update(
                "insert into evidence (ticket_id, file_name, file_type, file_size) values (?, 'error.png', 'image/png', 3)",
                first);
        String feedback = action == Action.APPROVED ? null : "Please check the token";

        assertThat(service.decide(reviewerId, Set.of(new UUID(0, 10)), List.of(second, first), action, feedback))
                .isEqualTo(2);

        String expectedStatus = action == Action.APPROVED ? "PUBLISHED" : action.name();
        for (UUID ticketId : List.of(first, second)) {
            assertThat(jdbc.queryForMap("""
                    select status, resolution_steps, review_due_date::text as due,
                           rejection_reason, change_request_comment
                    from ticket where ticket_id = ?
                    """, ticketId))
                    .containsEntry("status", expectedStatus)
                    .containsEntry("resolution_steps", "Restart")
                    .containsEntry("due", "2026-11-12")
                    .containsEntry("rejection_reason", action == Action.REJECTED ? feedback : null)
                    .containsEntry("change_request_comment", action == Action.CHANGES_REQUESTED ? feedback : null);
            assertThat(jdbc.queryForMap(
                            "select project_manager_id, decision, reason_or_comment, decided_at from review_decision where ticket_id = ?",
                            ticketId))
                    .containsEntry("project_manager_id", reviewerId)
                    .containsEntry("decision", action.name())
                    .containsEntry("reason_or_comment", feedback)
                    .hasEntrySatisfying("decided_at", value -> assertThat(value).isNotNull());
        }
        assertThat(jdbc.queryForObject("select file_name from evidence where ticket_id = ?", String.class, first))
                .isEqualTo("error.png");
    }

    @Test
    void refusesInvalidMissingUnmanagedAndStaleBatches_AC_009_7_BUG_139() {
        UUID pending = ticket("TEST-1", 10, "PENDING_REVIEW", null);
        UUID unmanaged = ticket("TEST-2", 20, "PENDING_REVIEW", null);
        UUID stale = ticket("TEST-3", 10, "REJECTED", null);
        UUID imported = ticket("TEST-4", 10, "PUBLISHED", null);
        jdbc.update("update ticket set source = 'CSV' where ticket_id = ?", imported);
        var scope = Set.of(new UUID(0, 10));

        assertThatThrownBy(() -> service.decide(reviewerId, scope, List.of(pending, pending), Action.APPROVED, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.decide(reviewerId, scope, List.of(pending), Action.REJECTED, " "))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                        service.decide(reviewerId, scope, List.of(pending), Action.CHANGES_REQUESTED, "x".repeat(1001)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                        service.decide(reviewerId, scope, List.of(pending, UUID.randomUUID()), Action.APPROVED, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.decide(reviewerId, scope, List.of(pending, unmanaged), Action.APPROVED, null))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> service.decide(reviewerId, scope, List.of(pending, stale), Action.APPROVED, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.decide(
                        reviewerId, scope, List.of(pending, imported), Action.CHANGES_REQUESTED, "Reason"))
                .isInstanceOf(ReviewSourceNotEligibleException.class);

        assertThat(status(pending)).isEqualTo("PENDING_REVIEW");
        assertThat(status(unmanaged)).isEqualTo("PENDING_REVIEW");
        assertThat(status(stale)).isEqualTo("REJECTED");
        assertThat(status(imported)).isEqualTo("PUBLISHED");
        assertThat(decisionCount()).isZero();
    }

    @Test
    void rollsBackTicketStateIfDecisionCannotBeSaved_AC_009_8() {
        UUID pending = ticket("TEST-1", 10, "PENDING_REVIEW", null);

        assertThatThrownBy(() -> service.decide(
                        UUID.randomUUID(), Set.of(new UUID(0, 10)), List.of(pending), Action.APPROVED, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(status(pending)).isEqualTo("PENDING_REVIEW");
        assertThat(decisionCount()).isZero();
    }

    @Test
    void rollsBackDecisionsIfAnyTicketUpdateFails_AC_009_8() {
        UUID first = ticket("TEST-1", 10, "PENDING_REVIEW", null);
        UUID second = ticket("TEST-2", 10, "PENDING_REVIEW", null);
        jdbc.execute("""
                alter table ticket add constraint test_reject_publish
                check (source_ticket_id <> 'TEST-2' or status <> 'PUBLISHED')
                """);
        try {
            assertThatThrownBy(() -> service.decide(
                            reviewerId, Set.of(new UUID(0, 10)), List.of(first, second), Action.APPROVED, null))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(status(first)).isEqualTo("PENDING_REVIEW");
            assertThat(status(second)).isEqualTo("PENDING_REVIEW");
            assertThat(decisionCount()).isZero();
        } finally {
            jdbc.execute("alter table ticket drop constraint test_reject_publish");
        }
    }

    @Test
    void commitsOnlyOneOfTwoCompetingDecisions_AC_009_7_AC_009_8() throws Exception {
        UUID pending = ticket("TEST-1", 10, "PENDING_REVIEW", null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approve = executor.submit(() -> {
                start.await();
                return catchThrowable(() ->
                        service.decide(reviewerId, Set.of(new UUID(0, 10)), List.of(pending), Action.APPROVED, null));
            });
            var reject = executor.submit(() -> {
                start.await();
                return catchThrowable(() -> service.decide(
                        reviewerId, Set.of(new UUID(0, 10)), List.of(pending), Action.REJECTED, "Reason"));
            });
            start.countDown();
            Throwable approved = approve.get(10, TimeUnit.SECONDS);
            Throwable rejected = reject.get(10, TimeUnit.SECONDS);
            assertThat(approved == null ? rejected : approved).isInstanceOf(BusinessRuleException.class);
            assertThat(approved == null || rejected == null).isTrue();
        }
        assertThat(status(pending)).isIn("PUBLISHED", "REJECTED");
        assertThat(decisionCount()).isEqualTo(1);
    }

    private UUID ticket(String key, long projectId, String status, UUID writerId) {
        return jdbc.queryForObject("""
                insert into ticket (source_ticket_id, project_id, source, status, writer_id, title, symptoms,
                                    root_cause, resolution_steps, scope, review_due_date)
                values (?, ?, 'SOLUTION_FORM', ?, ?, 'Login fails', 'Error', 'Token', 'Restart', 'Users', '2026-11-12')
                returning ticket_id
                """, UUID.class, key, new UUID(0, projectId), status, writerId);
    }

    private String status(UUID ticketId) {
        return jdbc.queryForObject("select status from ticket where ticket_id = ?", String.class, ticketId);
    }

    private OffsetDateTime queueEntryTime(UUID ticketId) {
        return jdbc.queryForObject(
                "select review_queue_entered_at from ticket where ticket_id = ?", OffsetDateTime.class, ticketId);
    }

    private int decisionCount() {
        return jdbc.queryForObject("select count(*) from review_decision", Integer.class);
    }
}
