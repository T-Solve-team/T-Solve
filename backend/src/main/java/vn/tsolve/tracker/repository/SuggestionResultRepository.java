package vn.tsolve.tracker.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.tracker.entity.SuggestionResult;

public interface SuggestionResultRepository extends JpaRepository<SuggestionResult, UUID> {

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

    /**
     * Records the MSG80 comment only while the result has none, so a rerun never replaces it (AC-012.3, AC-012.4).
     * Returns the number of rows changed: 0 when the row is gone or already has a comment.
     */
    @Transactional
    @Modifying
    @Query(
            """
            update SuggestionResult r
               set r.jiraCommentId = :commentId, r.commentedAt = :commentedAt
             where r.id = :id and r.jiraCommentId is null
            """)
    int markCommented(
            @Param("id") UUID id, @Param("commentId") String commentId, @Param("commentedAt") Instant commentedAt);
}
