package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.JiraServiceAccount;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SuggestionCommentServiceImplTest {

    private static final UUID ID = UUID.fromString("0c9f3e52-6a41-4d7b-8e2f-91b3c5d7a640");
    private static final JiraAccount ACCOUNT =
            new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token");
    private static final String SUGGESTION = "Update the VPN client to 5.2.";

    @Mock
    private JiraServiceAccount serviceAccount;

    @Mock
    private TicketTrackerClient jira;

    @Mock
    private SuggestionResultRepository repository;

    @Test
    void postsMsg80WithTheServiceAccountAndStoresTheCommentId_AC_012_1_AC_012_3() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findComment(ACCOUNT, "ITSUP-1", Msg80.PREFIX)).willReturn(Optional.empty());
        given(jira.addComment(ACCOUNT, "ITSUP-1", Msg80.paragraphs(SUGGESTION))).willReturn("10042");

        service().post(ID, "ITSUP-1", SUGGESTION);

        verify(repository).markCommented(eq(ID), eq("10042"), any(Instant.class));
    }

    @Test
    void storesTheIdOfACommentAlreadyPostedInsteadOfPostingAgain_AC_012_5() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findComment(ACCOUNT, "ITSUP-1", Msg80.PREFIX)).willReturn(Optional.of("10041"));

        service().post(ID, "ITSUP-1", SUGGESTION);

        verify(jira, never()).addComment(any(), anyString(), anyList());
        verify(repository).markCommented(eq(ID), eq("10041"), any(Instant.class));
    }

    @Test
    void postsNothingWithoutAServiceAccount_AC_012_8(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.empty());

        assertThatCode(() -> service().post(ID, "ITSUP-1", SUGGESTION)).doesNotThrowAnyException();

        verifyNoInteractions(jira, repository);
        assertThat(output).contains("ITSUP-1").doesNotContain(SUGGESTION);
    }

    @Test
    void endsWithoutRetryWhenJiraRejectsTheCall_AC_012_8(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findComment(ACCOUNT, "ITSUP-1", Msg80.PREFIX)).willReturn(Optional.empty());
        given(jira.addComment(any(), anyString(), anyList())).willThrow(new JiraRejectedException(403));

        assertThatCode(() -> service().post(ID, "ITSUP-1", SUGGESTION)).doesNotThrowAnyException();

        verifyNoInteractions(repository);
        assertThat(output)
                .contains("ITSUP-1")
                .contains("403")
                .doesNotContain(SUGGESTION)
                .doesNotContain("bot@acme.test")
                .doesNotContain("secret-token");
    }

    @Test
    void letsTheJobRetryWhenJiraIsUnavailable_AC_012_7() {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findComment(ACCOUNT, "ITSUP-1", Msg80.PREFIX)).willThrow(new JiraUnavailableException(503));

        assertThatThrownBy(() -> service().post(ID, "ITSUP-1", SUGGESTION))
                .isInstanceOf(JiraUnavailableException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void logsTheHttpStatusWhenJiraIsUnavailable_AC_012_7_BUG_166(CapturedOutput output) {
        given(serviceAccount.current()).willReturn(Optional.of(ACCOUNT));
        given(jira.findComment(ACCOUNT, "ITSUP-1", Msg80.PREFIX)).willReturn(Optional.empty());
        given(jira.addComment(any(), anyString(), anyList())).willThrow(new JiraUnavailableException(503));

        assertThatThrownBy(() -> service().post(ID, "ITSUP-1", SUGGESTION))
                .isInstanceOf(JiraUnavailableException.class);

        assertThat(output)
                .contains("ITSUP-1")
                .contains("503")
                .contains("JiraUnavailableException")
                .doesNotContain(SUGGESTION)
                .doesNotContain("secret-token");
    }

    private SuggestionCommentServiceImpl service() {
        return new SuggestionCommentServiceImpl(serviceAccount, jira, repository);
    }
}
