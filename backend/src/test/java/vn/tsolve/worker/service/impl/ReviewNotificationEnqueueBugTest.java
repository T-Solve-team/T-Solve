package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class ReviewNotificationEnqueueBugTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID RECORD_ID = new UUID(0, 3);

    private final EmailOutboxService outbox = mock(EmailOutboxService.class);
    private final ReviewNotificationService notifications = mock(ReviewNotificationService.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final TenantJobRequestHandler handler = mock(TenantJobRequestHandler.class);
    private final Logger enqueuerLogger = (Logger) LoggerFactory.getLogger(ReviewNotificationEnqueuer.class);
    private final ListAppender<ILoggingEvent> enqueuerLog = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        when(notifications.findNotification(any()))
                .thenReturn(Optional.of(new ReviewNotification(
                        ReviewNotification.Kind.REJECTED,
                        "writer@example.test",
                        "ITS-1",
                        "feedback",
                        RECORD_ID,
                        "Title",
                        "Project",
                        "Reviewer",
                        "Writer",
                        Instant.parse("2026-11-14T00:00:00Z"))));
        previousLevel = enqueuerLogger.getLevel();
        enqueuerLog.start();
        enqueuerLogger.addAppender(enqueuerLog);
        enqueuerLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        enqueuerLogger.detachAppender(enqueuerLog);
        enqueuerLog.stop();
        enqueuerLogger.setLevel(previousLevel);
    }

    @Test
    void enqueuesOnTheSoleActiveWorkspaceWhenNoneIsBound_BUG_164() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of(), new ThreadLocalTenantContext(id -> true));

        when(outbox.queue(any())).thenAnswer(call -> {
            assertThat(tenants.currentWorkspaceId()).contains(A);
            return UUID.randomUUID();
        });
        enqueuer(outbox, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID)));

        verify(outbox).queue(any(OutboundEmail.class));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void refusesWhenSeveralWorkspacesAreActiveAndNoneIsBound_BUG_164() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(), new ThreadLocalTenantContext(id -> true));
        assertThat(tenants.enqueueWorkspaceId()).isEmpty();

        enqueuer(outbox, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID)));

        verifyNoInteractions(scheduler, outbox);
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(
                        entry -> assertThat(entry.getFormattedMessage()).contains("(no Workspace to bind)", "1 of 1"));
    }

    @Test
    void directEnqueueStillRejectsAnUnboundThread_AC_002_2_BUG_164() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of(), new ThreadLocalTenantContext(id -> true));
        var jobs = new TenantJobServiceImpl(tenants, scheduler, handler);

        assertThatThrownBy(() -> jobs.enqueue("test-handler", RECORD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Enqueue requires a bound workspace");

        verifyNoInteractions(scheduler, outbox);
    }

    @Test
    void prefersTheBoundWorkspace_BUG_164() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of(), new ThreadLocalTenantContext(id -> true));

        when(outbox.queue(any())).thenAnswer(call -> {
            assertThat(tenants.currentWorkspaceId()).contains(B);
            return UUID.randomUUID();
        });
        tenants.runIn(B, () -> enqueuer(outbox, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID))));

        verify(outbox).queue(any(OutboundEmail.class));
    }

    @Test
    void keepsCurrentWorkspaceEmptyOutsideAJob_BUG_164() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of(), new ThreadLocalTenantContext(id -> true));

        assertThat(tenants.currentWorkspaceId()).isEmpty();
        tenants.runIn(A, () -> assertThat(tenants.currentWorkspaceId()).contains(A));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void continuesAfterAPerDecisionFailure_BUG_164() {
        when(outbox.queue(any(OutboundEmail.class))).thenThrow(new IllegalStateException("render failed"));
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4), new UUID(0, 5)));

        enqueuer(outbox, null).onDecided(event);

        verify(outbox, times(3)).queue(any(OutboundEmail.class));
    }

    @Test
    void continuesPastAMissingNotification_BUG_164() {
        when(notifications.findNotification(RECORD_ID)).thenReturn(Optional.empty());
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4)));

        enqueuer(outbox, null).onDecided(event);

        verify(outbox, times(1)).queue(any(OutboundEmail.class));
    }

    @Test
    void stopsAtTheFirstOutboxOutageWithOneWarning_BUG_164_BUG_226() {
        when(outbox.queue(any(OutboundEmail.class))).thenThrow(new EmailOutboxUnavailableException());
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4), new UUID(0, 5)));

        enqueuer(outbox, null).onDecided(event);

        verify(outbox, times(1)).queue(any(OutboundEmail.class));
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(entry ->
                        assertThat(entry.getFormattedMessage()).contains("3 of 3", "EmailOutboxUnavailableException"));
    }

    @Test
    void countsOnlyTheDecisionsAfterAnOutageAsRemaining_BUG_164_BUG_226() {
        when(outbox.queue(any(OutboundEmail.class)))
                .thenReturn(UUID.randomUUID())
                .thenThrow(new EmailOutboxUnavailableException());
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4), new UUID(0, 5)));

        enqueuer(outbox, null).onDecided(event);

        verify(outbox, times(2)).queue(any(OutboundEmail.class));
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getFormattedMessage()).contains("2 of 3"));
    }

    @Test
    void logsTheCountAndExceptionClassButNeverTheMessage_BUG_164() {
        when(outbox.queue(any(OutboundEmail.class)))
                .thenThrow(new IllegalStateException("smtp secret writer@example.test"));
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4), new UUID(0, 5)));

        enqueuer(outbox, null).onDecided(event);

        verify(outbox, times(3)).queue(any(OutboundEmail.class));
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .hasSize(3)
                .allSatisfy(
                        entry -> assertThat(entry.getFormattedMessage()).contains("1 of 3", "IllegalStateException"));
        assertThat(enqueuerLog.list).allSatisfy(entry -> {
            assertThat(entry.getFormattedMessage()).doesNotContain("writer@example.test", "smtp secret");
            assertThat(entry.getThrowableProxy()).isNull();
        });
    }

    @Test
    void enqueuesDirectlyOnAnAlreadyBoundThread_BUG_164() {
        var tenants =
                spy(new MockWorkerTenantService(Set.of(A, B), Set.of(), new ThreadLocalTenantContext(id -> true)));

        var boundWhenQueued = new java.util.ArrayList<Optional<UUID>>();
        doAnswer(invocation -> {
                    boundWhenQueued.add(tenants.currentWorkspaceId());
                    return null;
                })
                .when(outbox)
                .queue(any(OutboundEmail.class));

        tenants.runIn(B, () -> enqueuer(outbox, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID))));

        verify(outbox, times(1)).queue(any(OutboundEmail.class));
        assertThat(boundWhenQueued).containsExactly(Optional.of(B));
        verify(tenants, times(1)).runIn(any(UUID.class), any(Runnable.class));
        verify(tenants, never()).runIn(eq(A), any(Runnable.class));
    }

    @Test
    void logsWhenBindingTheWorkspaceFails_BUG_164() {
        WorkerTenantService tenants = mock(WorkerTenantService.class);
        when(tenants.currentWorkspaceId()).thenReturn(Optional.empty());
        when(tenants.enqueueWorkspaceId()).thenReturn(Optional.of(A));
        doThrow(new IllegalStateException("x")).when(tenants).runIn(eq(A), any(Runnable.class));

        enqueuer(outbox, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4))));

        verifyNoInteractions(outbox);
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(
                        entry -> assertThat(entry.getFormattedMessage()).contains("2 of 2", "IllegalStateException"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void warnsInsteadOfReturningSilentlyWhenTheOutboxIsMissing_BUG_339() {
        ObjectProvider<EmailOutboxService> outboxProvider = mock(ObjectProvider.class);
        ObjectProvider<WorkerTenantService> tenantProvider = mock(ObjectProvider.class);
        when(outboxProvider.getIfAvailable()).thenReturn(null);
        var enqueuer = new ReviewNotificationEnqueuer(
                outboxProvider, tenantProvider, notifications, mock(WorkspaceSiteService.class));

        enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4))));

        verifyNoInteractions(notifications, outbox);
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getFormattedMessage())
                        .contains("2 of 2", "email outbox is not available")
                        .doesNotContain("writer@example.test"));
    }

    @Test
    @DisplayName(
            "AC-002.12 in the web process the enqueuer queues for the Workspace bound in the holder and links its site")
    void queuesFromTheWebRequestAndLinksTheSiteWhenOnlyTheOutboxIsEnabled_AC_002_12() {
        var sites = mock(WorkspaceSiteService.class);
        when(sites.siteHost(A)).thenReturn(Optional.of("acme.t-solve.test"));

        runInWebProcess(
                sites, true, enqueuer -> enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID))));

        var queued = org.mockito.ArgumentCaptor.forClass(OutboundEmail.class);
        verify(outbox).queue(queued.capture());
        assertThat(queued.getValue().recipient()).isEqualTo("writer@example.test");
        assertThat(queued.getValue().bodyText()).contains("https://acme.t-solve.test/tickets/" + RECORD_ID);
    }

    @Test
    @DisplayName("AC-002.16 with nothing bound the enqueuer logs the missing Workspace once and queues nothing")
    void warnsThatNoWorkspaceIsBoundWhenTheWebOutboxFindsNone_AC_002_16() {
        runInWebProcess(
                mock(WorkspaceSiteService.class),
                false,
                enqueuer -> enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID))));

        verifyNoInteractions(outbox);
        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(
                        entry -> assertThat(entry.getFormattedMessage()).contains("1 of 1", "(no Workspace to bind)"));
    }

    @Test
    void namesTheEmailWhenTheOutboxRejectsIt_BUG_341() {
        when(outbox.queue(any())).thenThrow(new InvalidOutboundEmailException());

        runInWebProcess(
                mock(WorkspaceSiteService.class),
                true,
                enqueuer -> enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID))));

        assertThat(enqueuerLog.list)
                .filteredOn(entry -> entry.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getFormattedMessage())
                        .contains("1 of 1", "the email was rejected")
                        .doesNotContain("writer@example.test"));
    }

    /**
     * The staging web process: no WorkerTenantService bean, the holder is the only TenantContext. The enqueuer is
     * wired by Spring, so the test does not depend on how its constructor names the holder.
     */
    private void runInWebProcess(
            WorkspaceSiteService sites, boolean bound, java.util.function.Consumer<ReviewNotificationEnqueuer> action) {
        var holder = new ThreadLocalTenantContext(id -> true);
        new ApplicationContextRunner()
                .withBean(EmailOutboxService.class, () -> outbox)
                .withBean(ReviewNotificationService.class, () -> notifications)
                .withBean(WorkspaceSiteService.class, () -> sites)
                .withBean(ThreadLocalTenantContext.class, () -> holder)
                .withBean(ReviewNotificationEnqueuer.class)
                .run(context -> {
                    var enqueuer = context.getBean(ReviewNotificationEnqueuer.class);
                    if (bound) {
                        holder.runIn(A, () -> action.accept(enqueuer));
                    } else {
                        action.accept(enqueuer);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private ReviewNotificationEnqueuer enqueuer(EmailOutboxService outbox, WorkerTenantService tenants) {
        ObjectProvider<EmailOutboxService> outboxProvider = mock(ObjectProvider.class);
        ObjectProvider<WorkerTenantService> tenantProvider = mock(ObjectProvider.class);
        when(outboxProvider.getIfAvailable()).thenReturn(outbox);
        when(tenantProvider.getIfAvailable()).thenReturn(tenants);
        return new ReviewNotificationEnqueuer(
                outboxProvider, tenantProvider, notifications, mock(WorkspaceSiteService.class));
    }
}
