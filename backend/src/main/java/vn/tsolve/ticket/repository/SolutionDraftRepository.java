package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.entity.SolutionDraft;

public interface SolutionDraftRepository extends JpaRepository<SolutionDraft, UUID> {

    Optional<SolutionDraft> findByCreatorIdAndProjectIdAndIssueKey(UUID creatorId, UUID projectId, String issueKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from SolutionDraft d
            where d.creatorId = :creatorId and d.projectId = :projectId and d.issueKey = :issueKey
            """)
    int deleteDraft(
            @Param("creatorId") UUID creatorId, @Param("projectId") UUID projectId, @Param("issueKey") String issueKey);

    /**
     * Creates the Draft unless the creator already has one for the issue, which is never replaced (jira-app AC-005.8):
     * a Close at the same moment wins.
     *
     * @return 1 when created, 0 when a Draft was already there
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    insert into solution_draft (creator_id, project_id, jira_issue_key, content, created_at, updated_at)
                    values (:creatorId, :projectId, :issueKey, cast(:content as jsonb), :now, :now)
                    on conflict (creator_id, project_id, jira_issue_key) do nothing
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("creatorId") UUID creatorId,
            @Param("projectId") UUID projectId,
            @Param("issueKey") String issueKey,
            @Param("content") String content,
            @Param("now") Instant now);

    /**
     * Replaces the Draft's content only while nobody has saved it since it was created (jira-app AC-005.11): a Save
     * moves {@code updated_at} past {@code created_at}, and then the AI draft is dropped (AC-005.12).
     *
     * @return 1 when replaced, 0 when the Draft is gone or was saved
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    update solution_draft set content = cast(:content as jsonb), updated_at = :now
                    where creator_id = :creatorId and project_id = :projectId and jira_issue_key = :issueKey
                        and updated_at = created_at
                    """, nativeQuery = true)
    int replaceIfUnsaved(
            @Param("creatorId") UUID creatorId,
            @Param("projectId") UUID projectId,
            @Param("issueKey") String issueKey,
            @Param("content") String content,
            @Param("now") Instant now);

    /**
     * Drafts whose issue has no ticket of any source and is recorded in Done, newest saved first (jira-app AC-011.2,
     * AC-011.3); an issue never recorded is left out (KAN-371).
     */
    @Query("""
            select new vn.tsolve.ticket.dto.OpenDraft(d.creatorId, d.issueKey, d.updatedAt)
            from SolutionDraft d
            where not exists (select t.id from Ticket t where t.ticketId = d.issueKey)
                and exists (
                    select s.id from JiraIssueDoneState s
                    where s.projectId = d.projectId and s.issueKey = d.issueKey and s.done = true)
            order by d.updatedAt desc, d.issueKey
            """)
    List<OpenDraft> findOpenDrafts();

    /** The open Drafts of one creator, as {@link #findOpenDrafts()}, newest saved first (jira-app AC-011.3). */
    @Query("""
            select new vn.tsolve.ticket.dto.OpenDraft(d.creatorId, d.issueKey, d.updatedAt)
            from SolutionDraft d
            where d.creatorId = :creatorId
                and not exists (select t.id from Ticket t where t.ticketId = d.issueKey)
                and exists (
                    select s.id from JiraIssueDoneState s
                    where s.projectId = d.projectId and s.issueKey = d.issueKey and s.done = true)
            order by d.updatedAt desc, d.issueKey
            """)
    List<OpenDraft> findOpenDrafts(@Param("creatorId") UUID creatorId);
}
