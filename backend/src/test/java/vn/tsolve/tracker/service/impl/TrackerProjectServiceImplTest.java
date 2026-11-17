package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerCreateField;
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
            new JiraAccount("https://acme.atlassian.net", "1324a887-45db-1bf4-1e99-ef0ff456d421", "secret-token");

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
    void logsOnlyTheExceptionClassForAnUnexpectedClientFailure_AC_013_12(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findProject(ACCOUNT, "ITSUP"))
                .willThrow(new IllegalStateException("planted@mail.test planted-token-42 ftp://planted.example"));

        TrackerProjectLookup lookup = service().findProject("ITSUP");

        assertEmptyLookup(lookup, Outcome.UNAVAILABLE);
        assertThat(output)
                .contains("Jira project lookup ITSUP: UNAVAILABLE (IllegalStateException)")
                .doesNotContain("planted@mail.test", "planted-token-42", "ftp://planted.example", "planted");
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

    // --- KAN-121: the Chatbot project check (workspace STORY-012) ---

    private static final List<String> CHATBOT_PERMISSIONS = List.of("CREATE_ISSUES", "MODIFY_REPORTER");

    private void readyAccount() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.hasProjectPermissions(ACCOUNT, "OPS", CHATBOT_PERMISSIONS)).willReturn(true);
    }

    @Test
    void isReadyWhenOnlyFieldsTheChatbotSendsAreRequired_AC_012_7() {
        readyAccount();
        given(jira.createFields(ACCOUNT, "OPS", "Task"))
                .willReturn(Optional.of(List.of(
                        new TrackerCreateField("summary", true, false),
                        new TrackerCreateField("description", true, false),
                        new TrackerCreateField("project", true, false),
                        new TrackerCreateField("issuetype", true, false),
                        new TrackerCreateField("reporter", true, false),
                        new TrackerCreateField("labels", true, false),
                        new TrackerCreateField("customfield_10020", false, false))));

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.READY);
    }

    @Test
    void isReadyWhenAnotherRequiredFieldHasADefault_AC_012_7() {
        readyAccount();
        given(jira.createFields(ACCOUNT, "OPS", "Task"))
                .willReturn(Optional.of(List.of(
                        new TrackerCreateField("summary", true, false),
                        new TrackerCreateField("priority", true, true))));

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.READY);
    }

    @Test
    void isDeniedWithoutAServiceAccountAndCallsNoJira_AC_012_8() {
        given(serviceAccount.current()).willReturn(Optional.empty());

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.DENIED);
        verifyNoInteractions(jira);
    }

    @Test
    void isDeniedWhenAPermissionIsMissing_AC_012_8() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.hasProjectPermissions(ACCOUNT, "OPS", CHATBOT_PERMISSIONS)).willReturn(false);

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.DENIED);
        verify(jira, never()).createFields(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void isDeniedWhenJiraRefuses_AC_012_8(int status) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.hasProjectPermissions(ACCOUNT, "OPS", CHATBOT_PERMISSIONS))
                .willThrow(new JiraRejectedException(status));

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.DENIED);
    }

    @Test
    void isUnavailableWhenJiraCannotAnswer_AC_012_8() {
        readyAccount();
        for (RuntimeException failure : List.of(
                new JiraUnavailableException(500), new JiraUnavailableException(0), new IllegalStateException())) {
            willThrow(failure).given(jira).createFields(ACCOUNT, "OPS", "Task");

            assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.UNAVAILABLE);
        }
    }

    @Test
    void isNotReadyWithoutATaskType_AC_012_9() {
        readyAccount();
        given(jira.createFields(ACCOUNT, "OPS", "Task")).willReturn(Optional.empty());

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.NOT_READY);
    }

    @Test
    void isNotReadyWhenACustomFieldIsRequiredWithoutDefault_AC_012_9() {
        readyAccount();
        given(jira.createFields(ACCOUNT, "OPS", "Task"))
                .willReturn(Optional.of(List.of(
                        new TrackerCreateField("summary", true, false),
                        new TrackerCreateField("customfield_10010", true, false))));

        assertThat(service().checkChatbotProject("OPS")).isEqualTo(ChatbotProjectCheck.NOT_READY);
    }

    @Test
    void logsTheKeyAndJirasStatusOnly_AC_012_17(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.hasProjectPermissions(ACCOUNT, "OPS", CHATBOT_PERMISSIONS))
                .willThrow(new JiraRejectedException(403))
                .willThrow(new JiraUnavailableException(503))
                .willThrow(new IllegalStateException("JIRA-BODY-SECRET secret-token bot@acme.test"))
                .willReturn(false)
                .willReturn(true);
        given(jira.createFields(ACCOUNT, "OPS", "Task"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(List.of(new TrackerCreateField("customfield_1", true, false))))
                .willReturn(Optional.of(List.of()));

        for (int i = 0; i < 7; i++) {
            service().checkChatbotProject("OPS");
        }

        assertThat(output)
                .contains("Jira chatbot check OPS: DENIED (Jira answered 403)")
                .contains("Jira chatbot check OPS: UNAVAILABLE (Jira answered 503)")
                .contains("Jira chatbot check OPS: UNAVAILABLE (IllegalStateException)")
                .contains("Jira chatbot check OPS: DENIED (permission missing)")
                .contains("Jira chatbot check OPS: NOT_READY (no issue type Task)")
                .contains("Jira chatbot check OPS: NOT_READY (another field required)")
                .contains("Jira chatbot check OPS: READY (Jira answered 200)")
                .doesNotContain("JIRA-BODY-SECRET", "secret-token", "bot@acme.test", "customfield_1");
    }
}
