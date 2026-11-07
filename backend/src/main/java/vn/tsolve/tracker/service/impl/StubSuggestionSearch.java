package vn.tsolve.tracker.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.IssueSuggestion;
import vn.tsolve.tracker.service.SuggestionSearch;

/**
 * Stand-in for the AI Solution Suggestion of {@code search} until KAN-47 implements {@link SuggestionSearch}; delete
 * this class then. {@code TSOLVE_STUB_SUGGESTION}: empty = no suggestion, {@code unavailable} = search fails, any
 * other text = that suggested solution with no sources.
 */
@Service
public class StubSuggestionSearch implements SuggestionSearch {

    static final String UNAVAILABLE = "unavailable";

    private final String configured;

    public StubSuggestionSearch(@Value("${tsolve.stub.suggestion:}") String configured) {
        this.configured = configured == null ? "" : configured.strip();
    }

    @Override
    public Optional<IssueSuggestion> suggest(UUID projectId, String summary, String description) {
        if (configured.isEmpty()) {
            return Optional.empty();
        }
        if (UNAVAILABLE.equalsIgnoreCase(configured)) {
            throw new IllegalStateException("Stub suggestion search is configured unavailable");
        }
        return Optional.of(new IssueSuggestion(List.of(), configured));
    }
}
