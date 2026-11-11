package vn.tsolve.ticket.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.SolutionDraft;

public interface SolutionDraftRepository extends JpaRepository<SolutionDraft, UUID> {

    Optional<SolutionDraft> findByCreatorIdAndProjectIdAndIssueKey(UUID creatorId, UUID projectId, String issueKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            delete from SolutionDraft d
            where d.creatorId = :creatorId and d.projectId = :projectId and d.issueKey = :issueKey
            """)
    int deleteDraft(
            @Param("creatorId") UUID creatorId, @Param("projectId") UUID projectId, @Param("issueKey") String issueKey);
}
