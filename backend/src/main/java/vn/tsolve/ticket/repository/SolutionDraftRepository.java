package vn.tsolve.ticket.repository;

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

    /** Drafts whose issue has no ticket of any source, newest saved first (jira-app AC-011.2, AC-011.3). */
    @Query("""
            select new vn.tsolve.ticket.dto.OpenDraft(d.creatorId, d.issueKey, d.updatedAt)
            from SolutionDraft d
            where not exists (select t.id from Ticket t where t.ticketId = d.issueKey)
            order by d.updatedAt desc, d.issueKey
            """)
    List<OpenDraft> findOpenDrafts();

    /** The open Drafts of one creator, newest saved first (jira-app AC-011.3). */
    @Query("""
            select new vn.tsolve.ticket.dto.OpenDraft(d.creatorId, d.issueKey, d.updatedAt)
            from SolutionDraft d
            where d.creatorId = :creatorId
                and not exists (select t.id from Ticket t where t.ticketId = d.issueKey)
            order by d.updatedAt desc, d.issueKey
            """)
    List<OpenDraft> findOpenDrafts(@Param("creatorId") UUID creatorId);
}
