package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.dto.JiraIssueContext.Member;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.review.service.ReviewJiraAccess;

/**
 * The production {@link ReviewJiraAccess}: `ticket-review`'s feedback endpoint (MSG64, jira-app AC-008.7) applies the
 * same Jira App rule as the Solution Form — registered site, linked active Account, member of the issue's Project.
 */
@Service
public class JiraIssueAccessReviewAdapter implements ReviewJiraAccess {

    private final JiraIssueAccess access;
    private final AccountLookupService accounts;

    public JiraIssueAccessReviewAdapter(JiraIssueAccess access, AccountLookupService accounts) {
        this.access = access;
        this.accounts = accounts;
    }

    @Override
    public <T> T inWriterIssue(JiraAppPrincipal caller, String issueKey, Function<WriterIssue, T> read) {
        return access.inIssue(caller, issueKey, context -> {
            if (!context.member()) {
                throw new AccessDeniedException("Linked member of the issue's Project required");
            }
            Member member = context.requireMember();
            return read.apply(new WriterIssue(member.user().id(), member.projectId()));
        });
    }

    @Override
    public Optional<String> accountName(UUID accountId) {
        return accounts.findName(accountId);
    }
}
