package vn.tsolve.tracker.dto;

import java.util.List;

/**
 * A Jira project lookup for the Project module (STORY-013): only FOUND carries the canonical key and statuses; any
 * other outcome has a null key and no statuses, so no partial project crosses the boundary.
 */
public record TrackerProjectLookup(Outcome outcome, String key, List<TrackerStatus> statuses) {

    public TrackerProjectLookup {
        statuses = List.copyOf(statuses);
    }

    public static TrackerProjectLookup found(String key, List<TrackerStatus> statuses) {
        return new TrackerProjectLookup(Outcome.FOUND, key, statuses);
    }

    public static TrackerProjectLookup failed(Outcome outcome) {
        return new TrackerProjectLookup(outcome, null, List.of());
    }

    public enum Outcome {
        FOUND,
        NOT_FOUND,
        DENIED,
        UNAVAILABLE
    }
}
