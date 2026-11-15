package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.dto.JiraIssueContext;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.review.service.ReviewJiraAccess.WriterIssue;

class JiraIssueAccessReviewAdapterTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000126");
    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "b@x.test", "Ngo Bao", Set.of());
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-acc-1", null);

    private final JiraIssueAccess access = mock(JiraIssueAccess.class);
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final JiraIssueAccessReviewAdapter adapter = new JiraIssueAccessReviewAdapter(access, accounts);

    @Test
    void givesTicketReviewTheMembersAccountAndProject_AC_008_7() {
        givenContext(new JiraIssueContext(Optional.of(USER), Optional.of(PROJECT), true));

        WriterIssue writer = adapter.inWriterIssue(CALLER, "ITSUP-12", issue -> issue);

        assertThat(writer).isEqualTo(new WriterIssue(USER.id(), PROJECT));
    }

    @Test
    void refusesANonMemberBeforeTheFeedbackIsRead_AC_008_7() {
        givenContext(new JiraIssueContext(Optional.of(USER), Optional.of(PROJECT), false));
        AtomicBoolean read = new AtomicBoolean();

        assertThatThrownBy(() -> adapter.inWriterIssue(CALLER, "ITSUP-12", issue -> read.getAndSet(true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(read).isFalse();
    }

    @Test
    void refusesAnUnlinkedAccount_AC_008_7() {
        givenContext(new JiraIssueContext(Optional.empty(), Optional.of(PROJECT), false));

        assertThatThrownBy(() -> adapter.inWriterIssue(CALLER, "ITSUP-12", issue -> issue))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void readsTheStoredNameOfAnyAccount_AC_008_7() {
        UUID formerManager = UUID.fromString("00000000-0000-0000-0000-000000000009");
        given(accounts.findName(formerManager)).willReturn(Optional.of("Le Minh"));

        assertThat(adapter.accountName(formerManager)).contains("Le Minh");
    }

    @SuppressWarnings("unchecked")
    private void givenContext(JiraIssueContext context) {
        given(access.inIssue(eq(CALLER), eq("ITSUP-12"), any())).willAnswer(invocation -> {
            Function<JiraIssueContext, Object> work = invocation.getArgument(2);
            return work.apply(context);
        });
    }
}
