package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerComment;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;

@ExtendWith(OutputCaptureExtension.class)
class TrackerIssueServiceImplTest {

    private static final JiraAccount ACCOUNT = new JiraAccount("https://acme.atlassian.net", "cloud", "secret-token");

    private final JiraServiceAccount serviceAccount = mock(JiraServiceAccount.class);
    private final TicketTrackerClient jira = mock(TicketTrackerClient.class);
    private final TrackerIssueServiceImpl service = new TrackerIssueServiceImpl(serviceAccount, jira);

    @Test
    void returnsTheTextOfNonEmptyCommentsInOrder_AC_014_1() {
        when(serviceAccount.current()).thenReturn(Optional.of(ACCOUNT));
        when(jira.comments(ACCOUNT, "ITSUP-1"))
                .thenReturn(List.of(
                        new TrackerComment("reporter", "It fails"),
                        new TrackerComment("dev", "  "),
                        new TrackerComment("dev", "Restarted")));

        assertThat(service.comments("ITSUP-1")).containsExactly("It fails", "Restarted");
    }

    @Test
    void returnsNothingWithoutAServiceAccount_AC_014_2() {
        when(serviceAccount.current()).thenReturn(Optional.empty());

        assertThat(service.comments("ITSUP-1")).isEmpty();
        verifyNoInteractions(jira);
    }

    @Test
    void returnsNothingWhenJiraFails_AC_014_2_AC_014_3(CapturedOutput output) {
        when(serviceAccount.current()).thenReturn(Optional.of(ACCOUNT));
        for (RuntimeException failure : List.of(
                new JiraRejectedException(404), new JiraUnavailableException(503), new IllegalStateException("boom"))) {
            doThrow(failure).when(jira).comments(ACCOUNT, "ITSUP-1");

            assertThat(service.comments("ITSUP-1")).isEmpty();
        }
        assertThat(output.getAll()).contains("ITSUP-1", "404", "503").doesNotContain("secret-token", "boom");
    }
}
