package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.JiraServiceAccount;

/** project-integration STORY-011: role reads never throw; "not available" is an empty answer. */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TrackerProjectRolesTest {

    private static final JiraAccount ACCOUNT =
            new JiraAccount("https://acme.atlassian.net", "1324a887-45db-1bf4-1e99-ef0ff456d421", "app-token");

    @Mock
    private JiraServiceAccount serviceAccount;

    @Mock
    private TicketTrackerClient jira;

    private TrackerProjectServiceImpl service() {
        return new TrackerProjectServiceImpl(serviceAccount, jira);
    }

    @Test
    void readsTheRolesWithTheAppToken_AC_011_1() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.projectRoles(ACCOUNT, "DP")).willReturn(List.of(new TrackerRole(10015, "Member")));

        assertThat(service().projectRoles("DP")).contains(List.of(new TrackerRole(10015, "Member")));
    }

    @Test
    void readsTheActorsOfARole_AC_011_2() {
        TrackerRoleActors actors = new TrackerRoleActors(List.of(new TrackerRoleActors.Person("j-1", "An")), List.of());
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.roleActors(ACCOUNT, "DP", 10015)).willReturn(actors);

        assertThat(service().roleActors("DP", 10015)).contains(actors);
    }

    @Test
    void answersNothingWithoutAnAppToken_AC_011_4() {
        given(serviceAccount.current()).willReturn(Optional.empty());

        assertThat(service().projectRoles("DP")).isEmpty();
        verifyNoInteractions(jira);
    }

    @Test
    void answersNothingAndLogsOnlyTheStatusWhenJiraRefusesOrFails_AC_011_4(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.roleActors(ACCOUNT, "DP", 999)).willThrow(new JiraRejectedException(404));
        given(jira.projectRoles(ACCOUNT, "DP")).willThrow(new JiraUnavailableException(0));

        assertThat(service().roleActors("DP", 999)).isEmpty();
        assertThat(service().projectRoles("DP")).isEmpty();
        assertThat(output).contains("Jira answered 404").contains("no answer").doesNotContain("app-token");
    }
}
