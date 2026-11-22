package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.JiraIssueDoneState;

public interface JiraIssueDoneStateRepository extends JpaRepository<JiraIssueDoneState, UUID> {

    Optional<JiraIssueDoneState> findByProjectIdAndIssueKey(UUID projectId, String issueKey);

    /** One row per issue; a repeated event writes the same value again (jira-app AC-005.9). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    insert into jira_issue_done_state (project_id, jira_issue_key, done, created_at, updated_at)
                    values (:projectId, :issueKey, :done, :now, :now)
                    on conflict (project_id, jira_issue_key)
                    do update set done = excluded.done, updated_at = excluded.updated_at
                    """, nativeQuery = true)
    int record(
            @Param("projectId") UUID projectId,
            @Param("issueKey") String issueKey,
            @Param("done") boolean done,
            @Param("now") Instant now);
}
