package vn.tsolve.tracker.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.tracker.entity.SuggestionResult;

public interface SuggestionResultRepository extends JpaRepository<SuggestionResult, Long> {

    Optional<SuggestionResult> findByJiraIssueKey(String jiraIssueKey);

    /**
     * Stores the suggestion only while the issue has none, so a retried or parallel job never overwrites one
     * (AC-011.7). Returns the number of rows changed: 0 when the row is gone or already filled.
     */
    @Transactional
    @Modifying
    @Query(
            """
            update SuggestionResult r
               set r.sourcesUsed = :sourcesUsed, r.suggestedSolution = :suggestedSolution
             where r.jiraIssueKey = :jiraIssueKey and r.suggestedSolution is null
            """)
    int fillSuggestion(
            @Param("jiraIssueKey") String jiraIssueKey,
            @Param("sourcesUsed") String sourcesUsed,
            @Param("suggestedSolution") String suggestedSolution);
}
