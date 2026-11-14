package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.config.TicketClockConfig;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.ticket.service.TicketDecisionService;
import vn.tsolve.ticket.service.TicketDecisionService.Action;
import vn.tsolve.ticket.service.impl.ReviewNotificationServiceImpl;
import vn.tsolve.ticket.service.impl.TicketDecisionServiceImpl;
import vn.tsolve.ticket.service.impl.TicketTransitions;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/** A committed decision enqueues jobs, and each job sends one email from the stored decision (STORY-010). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TicketClockConfig.class,
    TicketDecisionServiceImpl.class,
    TicketTransitions.class,
    ReviewNotificationServiceImpl.class,
    ReviewNotificationEnqueuer.class
})
@ExtendWith(OutputCaptureExtension.class)
class ReviewNotificationIT {
    @org.springframework.boot.test.mock.mockito.MockBean
    private vn.tsolve.common.audit.AuditService audit;

    private static final UUID PROJECT = new UUID(0, 10);
    private static final String WRITER = "writer@example.test";
    private static final String FEEDBACK = "private-review-feedback";

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private TicketDecisionService decisions;

    @Autowired
    private ReviewNotificationService notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private TenantJobService jobs;

    private final SmtpEmailSender sender = mock(SmtpEmailSender.class);

    private UUID reviewerId;
    private UUID writerId;

    @BeforeEach
    void seed() {
        reset(jobs);
        jdbc.update("delete from review_decision");
        jdbc.update("delete from evidence");
        jdbc.update("delete from ticket");
        jdbc.update("delete from app_user");
        reviewerId = account("pm@example.test");
        writerId = account(WRITER);
        jdbc.update("insert into department (name) values ('Review notification') on conflict (name) do nothing");
        jdbc.update("""
                insert into project (public_id, department_id, jira_project_key)
                select ?, department_id, 'NOTIFY' from department where name = 'Review notification'
                on conflict (public_id) do nothing
                """, PROJECT);
    }

    @ParameterizedTest
    @EnumSource(
            value = Action.class,
            names = {"REJECTED", "CHANGES_REQUESTED"})
    void enqueuesOneJobPerCommittedDecision_AC_010_1(Action action) {
        UUID first = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID second = ticket("ITS-2", "PENDING_REVIEW", writerId);

        decisions.decide(reviewerId, Set.of(PROJECT), List.of(first, second), action, FEEDBACK);

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(jobs, times(2)).enqueue(eq(ReviewNotificationJobHandler.TYPE), ids.capture());
        assertThat(ids.getAllValues()).containsExactlyInAnyOrderElementsOf(decisionIds());
    }

    @Test
    void enqueuesNothingForApprove_AC_010_1() {
        UUID ticket = ticket("ITS-1", "PENDING_REVIEW", writerId);

        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.APPROVED, null);

        verify(jobs, never()).enqueue(anyString(), any());
    }

    @Test
    void enqueuesNothingWhenTheBatchRollsBack_AC_010_1() {
        UUID pending = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID stale = ticket("ITS-2", "REJECTED", writerId);

        assertThat(catchThrowable(() -> decisions.decide(
                        reviewerId, Set.of(PROJECT), List.of(pending, stale), Action.REJECTED, FEEDBACK)))
                .isNotNull();

        verify(jobs, never()).enqueue(anyString(), any());
    }

    @Test
    void keepsTheDecisionWhenEnqueueFailsAndLogsNoMailData_AC_010_3_AC_010_6(CapturedOutput output) {
        UUID ticket = ticket("ITS-1", "PENDING_REVIEW", writerId);
        when(jobs.enqueue(anyString(), any())).thenThrow(new IllegalStateException(WRITER + " " + FEEDBACK));

        assertThat(decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.REJECTED, FEEDBACK))
                .isEqualTo(1);

        assertThat(jdbc.queryForObject("select status from ticket where ticket_id = ?", String.class, ticket))
                .isEqualTo("REJECTED");
        assertThat(decisionIds()).hasSize(1);
        assertThat(output.getAll()).doesNotContain(WRITER).doesNotContain(FEEDBACK);
    }

    @ParameterizedTest
    @EnumSource(
            value = Action.class,
            names = {"REJECTED", "CHANGES_REQUESTED"})
    void sendsTheStoredDecisionToTheWriter_AC_010_2(Action action) {
        UUID ticket = ticket("ITS-7", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), action, FEEDBACK);

        handler().execute(decisionIds().get(0));

        Decision expected = action == Action.REJECTED ? Decision.REJECT : Decision.REQUEST_CHANGES;
        var outbound = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(sender).send(outbound.capture());
        assertThat(outbound.getValue().recipient()).isEqualTo(WRITER);
        assertThat(outbound.getValue().subject())
                .isEqualTo(
                        expected == Decision.REJECT
                                ? "[ITS-7] Your ticket was rejected"
                                : "[ITS-7] Changes requested for your ticket");
        assertThat(outbound.getValue().bodyText()).contains("ITS-7", FEEDBACK);
        assertThat(outbound.getValue().bodyHtml()).contains("ITS-7", FEEDBACK);
    }

    @Test
    void sendsNothingForApproveMissingDecisionOrNoWriter_AC_010_5() {
        UUID approved = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID anonymous = ticket("ITS-2", "PENDING_REVIEW", null);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(approved), Action.APPROVED, null);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(anonymous), Action.REJECTED, FEEDBACK);

        ReviewNotificationJobHandler handler = handler();
        for (UUID id : decisionIds()) {
            handler.execute(id);
        }
        handler.execute(UUID.randomUUID());

        verifyNoInteractions(sender);
    }

    private ReviewNotificationJobHandler handler() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SmtpEmailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new ReviewNotificationJobHandler(
                notifications, provider, mock(WorkerTenantService.class), mock(WorkspaceSiteService.class));
    }

    @Test
    void loadsTicketProjectNamesAndDecisionTime_AC_010_2_AC_010_7() {
        jdbc.update("update app_user set full_name = 'Reviewer Name' where id = ?", reviewerId);
        jdbc.update("update app_user set full_name = 'Writer Name' where id = ?", writerId);
        UUID ticket = ticket("ITS-8", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.REJECTED, FEEDBACK);
        var id = decisionIds().get(0);
        var notification = notifications.findNotification(id).orElseThrow();
        assertThat(notification.ticketId()).isEqualTo(ticket);
        assertThat(notification.ticketTitle()).isEqualTo("Login fails");
        assertThat(notification.projectName()).isEqualTo("NOTIFY");
        assertThat(notification.reviewerName()).isEqualTo("Reviewer Name");
        assertThat(notification.writerName()).isEqualTo("Writer Name");
        assertThat(notification.decidedAt())
                .isEqualTo(jdbc.queryForObject(
                                "select decided_at from review_decision where review_decision_id = ?",
                                java.sql.Timestamp.class,
                                id)
                        .toInstant());
    }

    @Test
    void loadsProjectKeyForLegacyProjectId_AC_010_2_BUG_213() {
        UUID ticket = ticket("ITS-10", "PENDING_REVIEW", writerId);
        UUID legacyProject = jdbc.queryForObject(
                "select lpad(to_hex(project_id), 32, '0')::uuid from project where public_id = ?", UUID.class, PROJECT);
        assertThat(legacyProject).isNotEqualTo(PROJECT);
        jdbc.update("update ticket set project_id = ? where ticket_id = ?", legacyProject, ticket);
        decisions.decide(reviewerId, Set.of(legacyProject), List.of(ticket), Action.REJECTED, FEEDBACK);

        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();

        assertThat(notification.recipient()).isEqualTo(WRITER);
        assertThat(notification.projectName()).isEqualTo("NOTIFY");
    }

    @Test
    @Transactional
    void loadsNotificationWhenReviewerAccountMissing_AC_010_2_BUG_213() {
        UUID ticket = ticket("ITS-11", "REJECTED", writerId);
        // Simulate an orphaned historical reference; rollback restores FK enforcement.
        jdbc.execute("set local session_replication_role = replica");
        UUID decision = jdbc.queryForObject("""
                insert into review_decision (ticket_id, project_manager_id, decision, reason_or_comment)
                values (?, ?, 'REJECTED', ?) returning review_decision_id
                """, UUID.class, ticket, UUID.randomUUID(), FEEDBACK);

        var notification = notifications.findNotification(decision).orElseThrow();

        assertThat(notification.recipient()).isEqualTo(WRITER);
        assertThat(notification.reviewerName()).isNullOrEmpty();
    }

    @Test
    void loadsNotificationWhenProjectMissing_AC_010_2_BUG_213() {
        UUID ticket = ticket("ITS-12", "PENDING_REVIEW", writerId);
        UUID missingProject = UUID.randomUUID();
        jdbc.update("update ticket set project_id = ? where ticket_id = ?", missingProject, ticket);
        decisions.decide(reviewerId, Set.of(missingProject), List.of(ticket), Action.REJECTED, FEEDBACK);

        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();

        assertThat(notification.recipient()).isEqualTo(WRITER);
        assertThat(notification.projectName()).isNullOrEmpty();
    }

    @Test
    void usesReviewerEmailWhenFullNameBlank_AC_010_7() {
        jdbc.update("update app_user set full_name = '  ' where id = ?", reviewerId);
        UUID ticket = ticket("ITS-13", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.CHANGES_REQUESTED, FEEDBACK);

        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();

        assertThat(notification.reviewerName()).isEqualTo("pm@example.test");
        handler().execute(decisionIds().get(0));
        var outbound = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(sender).send(outbound.capture());
        assertThat(outbound.getValue().recipient()).isEqualTo(WRITER);
        assertThat(outbound.getValue().bodyText()).contains("Reviewed by pm@example.test · ");
        assertThat(outbound.getValue().bodyHtml()).contains(">pm@example.test</b> · ");
    }

    @Test
    void usesReviewerEmailWhenFullNameMissing_AC_010_7() {
        UUID ticket = ticket("ITS-9", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.CHANGES_REQUESTED, FEEDBACK);
        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();
        assertThat(notification.reviewerName()).isEqualTo("pm@example.test");
        assertThat(notification.writerName()).isNull();
    }

    private List<UUID> decisionIds() {
        return jdbc.queryForList("select review_decision_id from review_decision", UUID.class);
    }

    private UUID account(String email) {
        return jdbc.queryForObject(
                "insert into app_user (email, status, created_at) values (?, 'ACTIVE', now()) returning id",
                UUID.class,
                email);
    }

    private UUID ticket(String key, String status, UUID writer) {
        return jdbc.queryForObject("""
                insert into ticket (source_ticket_id, project_id, source, status, writer_id, title, symptoms,
                                    root_cause, resolution_steps, scope, review_due_date)
                values (?, ?, 'SOLUTION_FORM', ?, ?, 'Login fails', 'Error', 'Token', 'Restart', 'Users', '2026-11-12')
                returning ticket_id
                """, UUID.class, key, PROJECT, status, writer);
    }
}
