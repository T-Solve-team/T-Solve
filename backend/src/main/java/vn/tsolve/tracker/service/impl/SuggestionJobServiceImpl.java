package vn.tsolve.tracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.entity.SuggestionResult;
import vn.tsolve.tracker.repository.SuggestionResultRepository;
import vn.tsolve.tracker.service.SuggestionJobService;
import vn.tsolve.tracker.service.SuggestionSearch;

// Not @Transactional: search may call the AI server, which must not run inside a transaction (AGENTS §4). The
// read and the conditional update each run in the repository's own transaction.
@Service
public class SuggestionJobServiceImpl implements SuggestionJobService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionJobServiceImpl.class);

    private final SuggestionResultRepository repository;
    private final SuggestionSearch search;
    private final ObjectMapper json;

    public SuggestionJobServiceImpl(SuggestionResultRepository repository, SuggestionSearch search, ObjectMapper json) {
        this.repository = repository;
        this.search = search;
        this.json = json;
    }

    @Override
    public void run(SuggestionJobRequest request) {
        String issueKey = request.issueKey();
        Optional<SuggestionResult> result = repository.findByJiraIssueKey(issueKey);
        if (result.isEmpty() || result.get().hasSuggestion()) {
            log.info("Suggestion job skipped, result gone or already filled: {}", issueKey);
            return;
        }

        Optional<IssueSuggestion> suggestion;
        try {
            suggestion = search.suggest(request.projectId(), request.summary(), request.description());
        } catch (RuntimeException e) {
            // Only the error type: the message may quote the issue text (Non-Functional 4).
            log.warn(
                    "Suggestion search failed for {}: {}",
                    issueKey,
                    e.getClass().getSimpleName());
            return;
        }
        if (suggestion.isEmpty() || isBlank(suggestion.get().suggestedSolution())) {
            log.info("No suggestion for {}", issueKey);
            return;
        }

        int changed = repository.fillSuggestion(
                issueKey, sourcesJson(suggestion.get()), suggestion.get().suggestedSolution());
        log.info(
                changed == 1 ? "Suggestion stored: {}" : "Suggestion not stored, result changed meanwhile: {}",
                issueKey);
    }

    private String sourcesJson(IssueSuggestion suggestion) {
        try {
            return json.writeValueAsString(suggestion.sources());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Suggestion sources are not serializable", e);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
