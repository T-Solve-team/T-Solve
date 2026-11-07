package vn.tsolve.tracker.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.JiraServiceAccount;
import vn.tsolve.tracker.service.SuggestionCommentService;

// Not @Transactional: Jira is called first and the id is stored after, each in its own step (AGENTS §4).
@Service
public class SuggestionCommentServiceImpl implements SuggestionCommentService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionCommentServiceImpl.class);

    private final JiraServiceAccount serviceAccount;
    private final TicketTrackerClient jira;
    private final SuggestionResultRepository repository;

    public SuggestionCommentServiceImpl(
            JiraServiceAccount serviceAccount, TicketTrackerClient jira, SuggestionResultRepository repository) {
        this.serviceAccount = serviceAccount;
        this.jira = jira;
        this.repository = repository;
    }

    @Override
    public void post(UUID suggestionId, String issueKey, String suggestedSolution) {
        Optional<JiraAccount> account = serviceAccount.current();
        if (account.isEmpty()) {
            log.warn("Suggestion comment not posted for {}: no Jira service account", issueKey);
            return;
        }
        String commentId;
        try {
            // A comment posted by an earlier run whose id was not stored is reused, never posted twice (AC-012.5).
            commentId = jira.findComment(account.get(), issueKey, Msg80.PREFIX)
                    .orElseGet(() -> jira.addComment(account.get(), issueKey, Msg80.paragraphs(suggestedSolution)));
        } catch (JiraRejectedException e) {
            log.warn("Suggestion comment not posted for {}: Jira answered {}", issueKey, e.getStatus());
            return;
        }
        repository.markCommented(suggestionId, commentId, Instant.now());
        log.info("Suggestion comment posted: {}", issueKey);
    }
}
