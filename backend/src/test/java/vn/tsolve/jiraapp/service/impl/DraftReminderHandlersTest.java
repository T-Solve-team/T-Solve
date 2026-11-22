package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.dto.DraftReminderDispatchRequest;
import vn.tsolve.jiraapp.dto.DraftReminderEmailRequest;
import vn.tsolve.jiraapp.dto.DraftReminderWorkspaceRequest;
import vn.tsolve.review.service.impl.ReviewExpiryRegistration;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith(OutputCaptureExtension.class)
class DraftReminderHandlersTest {

    private static final UUID WORKSPACE = new UUID(336, 100);
    private static final UUID OTHER_WORKSPACE = new UUID(336, 200);
    private static final UUID WRITER = new UUID(336, 1);
    private static final UUID SECOND = new UUID(336, 2);
    private static final String EMAIL = "ngo.bao@acme.example";
    // 2026-11-14 01:00 UTC is 08:00 in Asia/Ho_Chi_Minh.
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final OpenDraft DRAFT = new OpenDraft(WRITER, "SAM1-16", NOW);

    private final WorkerTenantService tenants = mock(WorkerTenantService.class);
    private final TenantExecutionService execution = mock(TenantExecutionService.class);
    private final JobRunrRuntime runtime = mock(JobRunrRuntime.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final SolutionDraftService drafts = mock(SolutionDraftService.class);
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final EmailOutboxService outbox = mock(EmailOutboxService.class);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void wire() {
        given(runtime.scheduler()).willReturn(scheduler);
        given(tenants.isWorkspaceActive(WORKSPACE)).willReturn(true);
        willAnswer(call -> {
                    ((Runnable) call.getArgument(1)).run();
                    return null;
                })
                .given(execution)
                .runInTenant(eq(WORKSPACE), any());
    }

    @Test
    void runsDailyAtEightInVietnamUnderItsOwnRecurringId_AC_011_1() {
        assertThat(DraftReminderRegistration.ID)
                .isEqualTo("jira-app-draft-reminder")
                .isNotEqualTo(ReviewExpiryRegistration.ID);
        assertThat(DraftReminderRegistration.CRON).isEqualTo("0 8 * * *");
        assertThat(DraftReminderRegistration.ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");

        new DraftReminderRegistration(runtime, "").register();

        verify(scheduler)
                .scheduleRecurrently(
                        eq("jira-app-draft-reminder"),
                        eq("0 8 * * *"),
                        eq(DraftReminderRegistration.ZONE),
                        any(DraftReminderDispatchRequest.class));
    }

    @Test
    void takesTheRunTimeFromTheEnvironment_AC_011_1() {
        new DraftReminderRegistration(runtime, " 30 17 * * 1-5 ").register();

        verify(scheduler)
                .scheduleRecurrently(
                        eq("jira-app-draft-reminder"),
                        eq("30 17 * * 1-5"),
                        eq(DraftReminderRegistration.ZONE),
                        any(DraftReminderDispatchRequest.class));
        assertThat(DraftReminderRegistration.cron(null)).isEqualTo("0 8 * * *");
    }

    @Test
    void refusesToStartWithAnInvalidCronNamingTheVariable_AC_011_1() {
        assertThatThrownBy(() -> new DraftReminderRegistration(runtime, "8am"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JIRA_APP_DRAFT_REMINDER_CRON");
    }

    @Test
    void queuesOneRunPerActiveWorkspaceForTodaysVietnamDate_AC_011_1() {
        given(tenants.activeWorkspaceIds()).willReturn(Set.of(WORKSPACE, OTHER_WORKSPACE));

        dispatch().run(new DraftReminderDispatchRequest());

        ArgumentCaptor<DraftReminderWorkspaceRequest> queued =
                ArgumentCaptor.forClass(DraftReminderWorkspaceRequest.class);
        verify(scheduler, times(2)).enqueue(queued.capture());
        assertThat(queued.getAllValues())
                .containsExactlyInAnyOrder(
                        new DraftReminderWorkspaceRequest(WORKSPACE, TODAY),
                        new DraftReminderWorkspaceRequest(OTHER_WORKSPACE, TODAY));
    }

    @Test
    void queuesTheEmailInTheOutboxInsideTheWritersWorkspace_AC_011_4_AC_011_6() {
        given(accounts.findActive(WRITER)).willReturn(Optional.of(new TSolveUser(WRITER, EMAIL, "Ngo Bao", Set.of())));
        // The outbox reads the bound Workspace, so the queue call must run inside runInTenant.
        boolean[] inTenant = {false};
        willAnswer(call -> {
                    inTenant[0] = true;
                    ((Runnable) call.getArgument(1)).run();
                    inTenant[0] = false;
                    return null;
                })
                .given(execution)
                .runInTenant(eq(WORKSPACE), any());
        given(drafts.findOpenDrafts(WRITER)).willReturn(List.of(DRAFT));
        willAnswer(call -> {
                    assertThat(inTenant[0]).isTrue();
                    return UUID.randomUUID();
                })
                .given(outbox)
                .queue(any());

        email("").run(new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY));

        verify(outbox).queue(any());
    }

    @Test
    void queuesOneEmailPerWriterWithADayKeyedJobId_AC_011_2_AC_011_5() {
        given(drafts.findOpenDrafts())
                .willReturn(List.of(DRAFT, new OpenDraft(SECOND, "SAM1-9", NOW), new OpenDraft(WRITER, "SAM1-8", NOW)));

        workspace().run(new DraftReminderWorkspaceRequest(WORKSPACE, TODAY));
        workspace().run(new DraftReminderWorkspaceRequest(WORKSPACE, TODAY));

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<DraftReminderEmailRequest> queued = ArgumentCaptor.forClass(DraftReminderEmailRequest.class);
        verify(scheduler, times(4)).enqueue(ids.capture(), queued.capture());
        assertThat(queued.getAllValues().subList(0, 2))
                .containsExactly(
                        new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY),
                        new DraftReminderEmailRequest(WORKSPACE, SECOND, TODAY));
        // The second run reuses the same job ids, so JobRunr does not queue the emails again.
        assertThat(ids.getAllValues().subList(2, 4))
                .isEqualTo(ids.getAllValues().subList(0, 2));
        assertThat(ids.getAllValues().get(0))
                .isNotEqualTo(DraftReminderWorkspaceHandler.jobId(WORKSPACE, WRITER, TODAY.plusDays(1)));
    }

    @Test
    void skipsAWorkspaceThatIsNoLongerActive_AC_011_1() {
        workspace().run(new DraftReminderWorkspaceRequest(OTHER_WORKSPACE, TODAY));

        verifyNoInteractions(scheduler, drafts);
    }

    @Test
    void mailsTheWritersOpenDraftsToTheirAccountEmail_AC_011_2_AC_011_3() {
        given(accounts.findActive(WRITER)).willReturn(Optional.of(new TSolveUser(WRITER, EMAIL, "Ngo Bao", Set.of())));
        given(drafts.findOpenDrafts(WRITER)).willReturn(List.of(DRAFT));

        email("https://acme.atlassian.net").run(new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY));

        ArgumentCaptor<OutboundEmail> queued = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(outbox).queue(queued.capture());
        assertThat(queued.getValue().recipient()).isEqualTo(EMAIL);
        assertThat(queued.getValue().subject()).isEqualTo("You have 1 unsubmitted Solution draft in T-Solve");
        assertThat(queued.getValue().bodyText()).contains("SAM1-16", "https://acme.atlassian.net/browse/SAM1-16");
        assertThat(queued.getValue().bodyHtml())
                .contains("Hi Ngo Bao,", "href=\"https://acme.atlassian.net/browse/SAM1-16\"");
    }

    @Test
    void mailsNobodyForAnInactiveAccountOrAWriterWithNoOpenDraftLeft_AC_011_2() {
        given(accounts.findActive(WRITER)).willReturn(Optional.empty());
        given(accounts.findActive(SECOND)).willReturn(Optional.of(new TSolveUser(SECOND, EMAIL, "Lan", Set.of())));
        given(drafts.findOpenDrafts(SECOND)).willReturn(List.of());

        email("").run(new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY));
        email("").run(new DraftReminderEmailRequest(WORKSPACE, SECOND, TODAY));

        verify(outbox, never()).queue(any());
    }

    @Test
    void failsOnlyThisWritersJobSoJobRunrRetriesItAndLogsNoAddress_AC_011_4_AC_011_7(CapturedOutput output) {
        given(accounts.findActive(WRITER)).willReturn(Optional.of(new TSolveUser(WRITER, EMAIL, "Ngo Bao", Set.of())));
        given(drafts.findOpenDrafts(WRITER)).willReturn(List.of(DRAFT));
        willThrow(new EmailOutboxUnavailableException()).given(outbox).queue(any());

        assertThatThrownBy(() -> email("").run(new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY)))
                .isInstanceOf(DraftReminderEmailHandler.EmailNotQueuedException.class)
                .hasMessageNotContaining(EMAIL);
        assertThat(output.getAll()).doesNotContain(EMAIL).doesNotContain("SAM1-16");
    }

    @Test
    void dropsAnAddressTheOutboxRejectsInsteadOfRetryingForever_AC_011_4_AC_011_7(CapturedOutput output) {
        given(accounts.findActive(WRITER)).willReturn(Optional.of(new TSolveUser(WRITER, EMAIL, "Ngo Bao", Set.of())));
        given(drafts.findOpenDrafts(WRITER)).willReturn(List.of(DRAFT));
        willThrow(new InvalidOutboundEmailException()).given(outbox).queue(any());

        email("").run(new DraftReminderEmailRequest(WORKSPACE, WRITER, TODAY));

        assertThat(output.getAll()).contains("invalid email").doesNotContain(EMAIL);
    }

    private DraftReminderDispatchHandler dispatch() {
        return new DraftReminderDispatchHandler(tenants, runtime, clock);
    }

    private DraftReminderWorkspaceHandler workspace() {
        return new DraftReminderWorkspaceHandler(tenants, execution, drafts, runtime);
    }

    private DraftReminderEmailHandler email(String site) {
        return new DraftReminderEmailHandler(tenants, execution, drafts, accounts, outbox, site);
    }
}
