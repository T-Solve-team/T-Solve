package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.TrackerProjectService;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TrackerProjectServiceImplTest {

    private static final JiraAccount ACCOUNT =
            new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token");

    @Mock
    private JiraServiceAccount serviceAccount;

    @Mock
    private TicketTrackerClient jira;

    @Test
    void readsProjectThenStatusesWithCanonicalKey_AC_013_1_AC_013_10() {
        List<TrackerStatus> statuses =
                List.of(new TrackerStatus("1", "Open", false), new TrackerStatus("2", "Done", true));
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "itsup")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willReturn(statuses);

        TrackerProjectLookup lookup = service().findProject("itsup");

        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(lookup.key()).isEqualTo("ITSUP");
        assertThat(lookup.statuses()).containsExactlyElementsOf(statuses);
        InOrder calls = inOrder(serviceAccount, jira);
        calls.verify(serviceAccount).current();
        calls.verify(jira).findProject(ACCOUNT, "itsup");
        calls.verify(jira).projectStatuses(ACCOUNT, "ITSUP");
        calls.verifyNoMoreInteractions();
    }

    @Test
    void deduplicatesStatusesInFirstSeenOrderKeepingFirstNameAndDone_AC_013_2_AC_013_3() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP"))
                .willReturn(List.of(
                        new TrackerStatus("1", "Open", false),
                        new TrackerStatus("2", "Done", true),
                        new TrackerStatus("1", "Reopened", true),
                        new TrackerStatus("3", "Review", false)));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(lookup.key()).isEqualTo("ITSUP");
        assertThat(lookup.statuses())
                .containsExactly(
                        new TrackerStatus("1", "Open", false),
                        new TrackerStatus("2", "Done", true),
                        new TrackerStatus("3", "Review", false));
    }

    @Test
    void findsProjectWithNoStatuses_AC_013_9() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willReturn(List.of());

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(lookup.key()).isEqualTo("ITSUP");
        assertThat(lookup.statuses()).isEmpty();
        verify(jira).projectStatuses(ACCOUNT, "ITSUP");
    }

    @Test
    void returnsNotFoundWithoutReadingStatusesWhenProjectIsMissing_AC_013_4_AC_013_11() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willReturn(Optional.empty());

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.NOT_FOUND);
        verify(jira, never()).projectStatuses(any(), anyString());
    }

    @Test
    void returnsNotFoundWhenProjectReadThrows404_AC_013_4_AC_013_11() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willThrow(new JiraRejectedException(404));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.NOT_FOUND);
        verify(jira, never()).projectStatuses(any(), anyString());
    }

    @Test
    void returnsNotFoundWithoutPartialProjectWhenStatusesAreMissing_AC_013_4() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "itsup")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willThrow(new JiraRejectedException(404));

        TrackerProjectLookup lookup = service().findProject("itsup");

        assertEmptyLookup(lookup, Outcome.NOT_FOUND);
    }

    @Test
    void deniesLookupWithoutCallingJiraWhenServiceAccountIsMissing_AC_013_5() {
        given(serviceAccount.current()).willReturn(Optional.empty());

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.DENIED);
        verify(serviceAccount).current();
        verifyNoInteractions(jira);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 400})
    void deniesRejectedProjectReadsWithoutReadingStatuses_AC_013_6_AC_013_11(int status) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willThrow(new JiraRejectedException(status));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.DENIED);
        verify(jira, never()).projectStatuses(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 400})
    void deniesRejectedStatusesReadsWithoutPartialProject_AC_013_6(int status) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "itsup")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willThrow(new JiraRejectedException(status));

        TrackerProjectLookup lookup = service().findProject("itsup");

        assertEmptyLookup(lookup, Outcome.DENIED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 429, 0})
    void reportsUnavailableProjectWithoutReadingStatuses_AC_013_7_AC_013_8_AC_013_11(int status) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willThrow(new JiraUnavailableException(status));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.UNAVAILABLE);
        verify(jira, never()).projectStatuses(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {503, 429, 0})
    void reportsUnavailableStatusesWithoutPartialProject_AC_013_7_AC_013_8(int status) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "itsup")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willThrow(new JiraUnavailableException(status));

        TrackerProjectLookup lookup = service().findProject("itsup");

        assertEmptyLookup(lookup, Outcome.UNAVAILABLE);
    }

    @Test
    void logsFoundKeyStatusAndOutcomeWithoutCredentialsOrBody_AC_013_12(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP"))
                .willReturn(List.of(new TrackerStatus("1", "JIRA-BODY-SECRET", false)));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(output)
                .contains("ITSUP", "FOUND", "200")
                .doesNotContain("bot@acme.test", "secret-token", "JIRA-BODY-SECRET", ACCOUNT.siteUrl());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 500, 0})
    void logsFailureKeyStatusAndOutcomeWithoutCredentials_AC_013_11_AC_013_12(int status, CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        Outcome outcome;
        if (status == 401) {
            given(jira.findProject(ACCOUNT, "ITSUP")).willThrow(new JiraRejectedException(status));
            outcome = Outcome.DENIED;
        } else {
            given(jira.findProject(ACCOUNT, "ITSUP")).willThrow(new JiraUnavailableException(status));
            outcome = Outcome.UNAVAILABLE;
        }

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, outcome);
        verify(jira, never()).projectStatuses(any(), anyString());
        assertThat(output)
                .contains("ITSUP", outcome.name())
                .doesNotContain("bot@acme.test", "secret-token", "JIRA-BODY-SECRET", ACCOUNT.siteUrl());
        if (status == 0) {
            assertThat(output).contains("no answer").doesNotContain("answered 0");
        } else {
            assertThat(output).contains(Integer.toString(status));
        }
    }

    @Test
    void returnsOnlyProjectDataUsingTwoReadDependencies_AC_013_13() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP")).willReturn(Optional.of("ITSUP"));
        given(jira.projectStatuses(ACCOUNT, "ITSUP")).willReturn(List.of(new TrackerStatus("1", "Open", false)));
        TrackerProjectService lookupService = new TrackerProjectServiceImpl(serviceAccount, jira);

        TrackerProjectLookup lookup = lookupService.findProject("ITSUP");

        assertThat(lookup)
                .isEqualTo(new TrackerProjectLookup(
                        Outcome.FOUND, "ITSUP", List.of(new TrackerStatus("1", "Open", false))));
        assertThat(lookup.toString())
                .doesNotContain("bot@acme.test", "secret-token", "JIRA-BODY-SECRET", ACCOUNT.siteUrl());
        verify(serviceAccount).current();
        verify(jira).findProject(ACCOUNT, "ITSUP");
        verify(jira).projectStatuses(ACCOUNT, "ITSUP");
        verifyNoMoreInteractions(serviceAccount, jira);
    }

    private TrackerProjectService service() {
        return new TrackerProjectServiceImpl(serviceAccount, jira);
    }

    private static void assertEmptyLookup(TrackerProjectLookup lookup, Outcome outcome) {
        assertThat(lookup.outcome()).isEqualTo(outcome);
        assertThat(lookup.key()).isNull();
        assertThat(lookup.statuses()).isNotNull().isEmpty();
        assertThat(lookup.toString()).doesNotContain("bot@acme.test", "secret-token", "JIRA-BODY-SECRET");
    }
}
