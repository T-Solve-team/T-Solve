package vn.tsolve.tracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.exception.SuggestionJobFailedException;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionCommentService;
import vn.tsolve.tracker.service.SuggestionJobService;
import vn.tsolve.tracker.service.SuggestionSearch;

// Not @Transactional: search may call the AI server, which must not run inside a transaction (AGENTS §4). The
// read and the conditional update each run in the repository's own transaction.
@Service
public class SuggestionJobServiceImpl implements SuggestionJobService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionJobServiceImpl.class);

    private final SuggestionResultRepository repository;
    private final SuggestionSearch search;
    private final SuggestionCommentService comments;
    private final ObjectMapper json;

    public SuggestionJobServiceImpl(
            SuggestionResultRepository repository,
            SuggestionSearch search,
            SuggestionCommentService comments,
            ObjectMapper json) {
        this.repository = repository;
        this.search = search;
        this.comments = comments;
        this.json = json;
    }

    @Override
    public void run(UUID suggestionId) {
        SuggestionResult result;
        try {
            Optional<SuggestionResult> found = repository.findById(suggestionId);
            if (found.isEmpty()) {
                log.info("Suggestion job skipped, result gone: {}", suggestionId);
                return;
            }
            result = found.get();
        } catch (RuntimeException e) {
            throw failed(String.valueOf(suggestionId), e);
        }
        String issueKey = result.getJiraIssueKey();
        if (result.hasComment()) {
            log.info("Suggestion job skipped, comment already posted: {}", issueKey);
            return;
        }
        try {
            // A rerun of a job that stored the suggestion but could not post it only posts it (AC-011.7, AC-012.3).
            Optional<String> stored =
                    result.hasSuggestion() ? Optional.of(result.getSuggestedSolution()) : suggest(result);
            if (stored.isPresent()) {
                comments.post(suggestionId, issueKey, stored.get());
            }
        } catch (RuntimeException e) {
            throw failed(issueKey, e);
        }
    }

    /** The suggestion this run stored, or empty when there is none or another run stored one meanwhile. */
    private Optional<String> suggest(SuggestionResult result) {
        String issueKey = result.getJiraIssueKey();
        Optional<IssueSuggestion> suggestion = search.suggest(
                result.getProjectId(), text(result.getIssueSummary()), text(result.getIssueDescription()));
        if (suggestion.isEmpty() || isBlank(suggestion.get().suggestedSolution())) {
            log.info("No suggestion for {}", issueKey);
            return Optional.empty();
        }
        String suggestedSolution = suggestion.get().suggestedSolution();
        int changed = repository.fillSuggestion(issueKey, sourcesJson(suggestion.get()), suggestedSolution);
        if (changed != 1) {
            log.info("Suggestion not stored, result changed meanwhile: {}", issueKey);
            return Optional.empty();
        }
        log.info("Suggestion stored: {}", issueKey);
        return Optional.of(suggestedSolution);
    }

    // Only the error type, logged and carried: the original message may quote the issue text (Non-Functional 4).
    private static SuggestionJobFailedException failed(String issueKey, RuntimeException e) {
        String type = e.getClass().getSimpleName();
        log.warn("Suggestion job failed for {}: {}", issueKey, type);
        return new SuggestionJobFailedException(issueKey, type);
    }

    private String sourcesJson(IssueSuggestion suggestion) {
        try {
            return json.writeValueAsString(suggestion.sources());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Suggestion sources are not serializable");
        }
    }

    // Rows recorded before the issue text was stored have none (edge case 48).
    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
