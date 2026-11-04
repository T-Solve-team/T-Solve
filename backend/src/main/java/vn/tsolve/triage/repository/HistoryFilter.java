package vn.tsolve.triage.repository;

import java.time.Instant;
import java.util.Set;

/**
 * A validated history query: the Project ids to read (already limited to the caller's), the answer ({@code YES},
 * {@code NO}, {@code NONE} = not answered, or null for all) and the half-open time range, or nulls for no range.
 */
public record HistoryFilter(Set<Long> projectIds, String answer, Instant start, Instant end) {}
