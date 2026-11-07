package vn.tsolve.tracker.dto;

import java.util.List;

/** What search suggests for a new issue (AC-011.4): the sources it used and the suggested solution text. */
public record IssueSuggestion(List<SuggestionSource> sources, String suggestedSolution) {

    public IssueSuggestion {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
