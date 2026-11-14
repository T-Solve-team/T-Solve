package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.config.TicketClockConfig;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.ticket.service.TicketDecisionService;
import vn.tsolve.ticket.service.TicketDecisionService.Action;
import vn.tsolve.ticket.service.impl.ReviewNotificationServiceImpl;
import vn.tsolve.ticket.service.impl.TicketDecisionServiceImpl;
import vn.tsolve.ticket.service.impl.TicketTransitions;
import vn.tsolve.worker.WorkerJobsDatabase;
import vn.tsolve.worker.client.ReviewEmail;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.ReviewEmailRenderer;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

/** Committed decisions store rendered mail in the separate Jobs database (STORY-010). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TicketClockConfig.class,
    TicketDecisionServiceImpl.class,
    TicketTransitions.class,
    ReviewNotificationServiceImpl.class,
    ReviewNotificationEnqueuer.class,
    ReviewNotificationIT.OutboxConfig.class
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

    @SpyBean
    private EmailOutboxService outbox;

    @Autowired
    private OutboxDatabase outboxDatabase;

    private JdbcTemplate jobsJdbc;

    @Autowired
    private EmailOutboxRepository outboxRepository;

    @Autowired
    private WorkerTenantService tenants;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReviewNotificationEnqueuer enqueuer;

    @Autowired
    private ApplicationEventPublisher events;

    @MockBean
    private WorkspaceSiteService sites;

    private static final UUID WORKSPACE = new UUID(0, 100);
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @TestConfiguration
    static class OutboxConfig {
        @Bean
        WorkerTenantService tenants() {
            return new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        }

        @Bean
        OutboxDatabase outboxDatabase() throws Exception {
            var db = WorkerJobsDatabase.instance();
            var dataSource = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            var migration = new SpringLiquibase();
            migration.setDataSource(dataSource);
            migration.setChangeLog("classpath:db/jobs-changelog/db.changelog-master.yaml");
            migration.afterPropertiesSet();
            return new OutboxDatabase(new JdbcTemplate(dataSource));
        }

        @Bean
        EmailOutboxRepository outboxRepository(OutboxDatabase database) {
            return new EmailOutboxRepository(database.jdbc().getDataSource());
        }

        @Bean
        EmailOutboxService outbox(EmailOutboxRepository repository, WorkerTenantService tenants) {
            return new EmailOutboxServiceImpl(repository, tenants, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    record OutboxDatabase(JdbcTemplate jdbc) {}

    private final SmtpEmailSender sender = mock(SmtpEmailSender.class);

    private UUID reviewerId;
    private UUID writerId;

    @BeforeEach
    void seed() {
        jobsJdbc = outboxDatabase.jdbc();
        reset(outbox);
        jobsJdbc.update("delete from email_outbox");
        when(sites.siteHost(WORKSPACE)).thenReturn(Optional.of("acme.example.test"));
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
    void enqueuesOneEmailPerCommittedDecision_AC_010_1(Action action) {
        UUID first = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID second = ticket("ITS-2", "PENDING_REVIEW", writerId);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            decisions.decide(reviewerId, Set.of(PROJECT), List.of(first, second), action, FEEDBACK);
            assertThat(outboxCount()).isZero();
        });
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(jobsJdbc.queryForList("select workspace_id from email_outbox", UUID.class))
                .containsOnly(WORKSPACE);
    }

    @Test
    void enqueuesNothingForApprove_AC_010_1() {
        UUID ticket = ticket("ITS-1", "PENDING_REVIEW", writerId);

        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.APPROVED, null);

        assertThat(outboxCount()).isZero();
    }

    @Test
    void enqueuesNothingWhenTheBatchRollsBack_AC_010_1() {
        UUID pending = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID stale = ticket("ITS-2", "REJECTED", writerId);

        assertThat(catchThrowable(() -> decisions.decide(
                        reviewerId, Set.of(PROJECT), List.of(pending, stale), Action.REJECTED, FEEDBACK)))
                .isNotNull();

        assertThat(outboxCount()).isZero();
    }

    @Test
    void keepsTheDecisionWhenEnqueueFailsAndLogsNoMailData_AC_010_3_AC_010_6(CapturedOutput output) {
        UUID ticket = ticket("ITS-1", "PENDING_REVIEW", writerId);
        doThrow(new IllegalStateException(WRITER + " " + FEEDBACK)).when(outbox).queue(any());

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
    void queuesTheStoredDecisionToTheWriter_AC_010_2(Action action) {
        UUID ticket = ticket("ITS-7", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), action, FEEDBACK);

        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();
        var email = new ReviewEmail(
                action == Action.REJECTED ? Decision.REJECT : Decision.REQUEST_CHANGES,
                notification.ticketKey(),
                notification.ticketTitle(),
                notification.projectName(),
                notification.reviewerName(),
                notification.writerName(),
                notification.decidedAt(),
                notification.feedback(),
                "https://acme.example.test/tickets/" + ticket,
                "acme.example.test");
        var renderer = new ReviewEmailRenderer();
        var row = jobsJdbc.queryForMap("select * from email_outbox");
        assertThat(row)
                .containsEntry("recipient", WRITER)
                .containsEntry("subject", renderer.subject(email))
                .containsEntry("body_text", renderer.plain(email))
                .containsEntry("body_html", renderer.html(email));
    }

    @Test
    void sendsNothingForApproveMissingDecisionOrNoWriter_AC_010_5() {
        UUID approved = ticket("ITS-1", "PENDING_REVIEW", writerId);
        UUID anonymous = ticket("ITS-2", "PENDING_REVIEW", null);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(approved), Action.APPROVED, null);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(anonymous), Action.REJECTED, FEEDBACK);
        var ids = new ArrayList<>(decisionIds());
        ids.add(UUID.randomUUID());
        tenants.runIn(WORKSPACE, () -> enqueuer.onDecided(new ReviewFeedbackDecidedEvent(ids)));
        assertThat(outboxCount()).isZero();
        UUID addressed = ticket("ITS-3", "PENDING_REVIEW", writerId);
        UUID anotherAnonymous = ticket("ITS-4", "PENDING_REVIEW", null);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(addressed, anotherAnonymous), Action.REJECTED, FEEDBACK);
        assertThat(outboxCount()).isEqualTo(1);
    }

    @Test
    void queuesNothingWhenSuccessfulDecisionTransactionIsRolledBack_AC_010_1() {
        UUID ticket = ticket("ITS-rollback", "PENDING_REVIEW", writerId);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.REJECTED, FEEDBACK);
            assertThat(outboxCount()).isZero();
            status.setRollbackOnly();
        });
        assertThat(outboxCount()).isZero();
        assertThat(decisionIds()).isEmpty();
        assertThat(jdbc.queryForObject("select status from ticket where ticket_id = ?", String.class, ticket))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void retriesAndFailsQueuedMailWithoutChangingDecision_AC_010_4() {
        UUID ticket = ticket("ITS-retry", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.REJECTED, FEEDBACK);
        var storedDecision = jdbc.queryForMap("select * from review_decision");
        var storedTicket = jdbc.queryForMap("select * from ticket where ticket_id = ?", ticket);
        doThrow(mock(SmtpEmailSender.DeliveryException.class)).when(sender).send(any());
        var beans = new StaticListableBeanFactory();
        beans.addBean("sender", sender);
        var properties = new EmailOutboxProperties(
                Duration.ofSeconds(30),
                List.of(Duration.ofMinutes(5), Duration.ofMinutes(15)),
                Duration.ofMinutes(5),
                20,
                Duration.ofDays(30));
        long[] minutes = {0, 5, 20};
        for (int attempt = 0; attempt < minutes.length; attempt++) {
            Instant now = NOW.plus(Duration.ofMinutes(minutes[attempt]));
            new EmailOutboxDispatchHandler(
                            outboxRepository,
                            tenants,
                            beans.getBeanProvider(SmtpEmailSender.class),
                            properties,
                            Clock.fixed(now, ZoneOffset.UTC))
                    .run(new EmailOutboxDispatchRequest());
            var row = jobsJdbc.queryForMap("select * from email_outbox");
            assertThat(row)
                    .containsEntry("attempt_count", attempt + 1)
                    .containsEntry("status", attempt == 2 ? "FAILED" : "PENDING")
                    .containsEntry("claimed_at", null);
            if (attempt < 2) {
                assertThat(((java.sql.Timestamp) row.get("next_attempt_at")).toInstant())
                        .isEqualTo(NOW.plus(Duration.ofMinutes(minutes[attempt + 1])));
            }
            assertThat(jdbc.queryForMap("select * from review_decision")).isEqualTo(storedDecision);
            assertThat(jdbc.queryForMap("select * from ticket where ticket_id = ?", ticket))
                    .isEqualTo(storedTicket);
        }
        verify(sender, times(3)).send(any());
    }

    private int outboxCount() {
        return jobsJdbc.queryForObject("select count(*) from email_outbox", Integer.class);
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
        assertThat((String) jobsJdbc.queryForMap("select * from email_outbox").get("body_text"))
                .contains("NOTIFY");
    }

    @Test
    void loadsNotificationWhenReviewerAccountMissing_AC_010_2_BUG_213() {
        UUID ticket = ticket("ITS-11", "REJECTED", writerId);
        UUID decision = new TransactionTemplate(transactionManager).execute(status -> {
            // Simulate an orphaned historical reference; commit restores FK enforcement.
            jdbc.execute("set local session_replication_role = replica");
            UUID id = jdbc.queryForObject("""
                    insert into review_decision (ticket_id, project_manager_id, decision, reason_or_comment)
                    values (?, ?, 'REJECTED', ?) returning review_decision_id
                    """, UUID.class, ticket, UUID.randomUUID(), FEEDBACK);
            events.publishEvent(new ReviewFeedbackDecidedEvent(List.of(id)));
            return id;
        });

        var notification = notifications.findNotification(decision).orElseThrow();

        assertThat(notification.recipient()).isEqualTo(WRITER);
        assertThat(notification.reviewerName()).isNullOrEmpty();
        var row = jobsJdbc.queryForMap("select * from email_outbox");
        assertThat((String) row.get("body_text")).doesNotContain("Reviewed by");
        assertThat((String) row.get("body_html")).doesNotContain("Reviewed by");
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
        assertThat(outboxCount()).isEqualTo(1);
        assertThat((String) jobsJdbc.queryForMap("select * from email_outbox").get("body_html"))
                .contains(">ITS-12</div>")
                .doesNotContain("ITS-12 \u00b7");
    }

    @Test
    void usesReviewerEmailWhenFullNameBlank_AC_010_7() {
        jdbc.update("update app_user set full_name = '  ' where id = ?", reviewerId);
        UUID ticket = ticket("ITS-13", "PENDING_REVIEW", writerId);
        decisions.decide(reviewerId, Set.of(PROJECT), List.of(ticket), Action.CHANGES_REQUESTED, FEEDBACK);

        var notification = notifications.findNotification(decisionIds().get(0)).orElseThrow();

        assertThat(notification.reviewerName()).isEqualTo("pm@example.test");

        var row = jobsJdbc.queryForMap("select * from email_outbox");
        assertThat(row).containsEntry("recipient", WRITER);
        assertThat((String) row.get("body_text")).contains("Reviewed by pm@example.test \u00b7 ");
        assertThat((String) row.get("body_html")).contains(">pm@example.test</b> \u00b7 ");
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
