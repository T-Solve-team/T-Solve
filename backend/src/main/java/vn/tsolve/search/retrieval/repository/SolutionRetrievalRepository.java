package vn.tsolve.search.retrieval.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface SolutionRetrievalRepository extends Repository<SearchTicket, UUID> {
    @Query(
            value =
                    """
                    SELECT t.symptoms AS symptoms, t.root_cause AS "rootCause",
                           t.resolution_steps AS "resolutionSteps", t.workaround AS workaround,
                           t.scope AS "scopeOfApplicability",
                           1 - (e.embedding <=> CAST(:vector AS vector)) AS similarity
                    FROM ticket_embedding e JOIN ticket t ON t.ticket_id = e.ticket_id
                    JOIN project p ON (p.public_id = t.project_id
                         OR lpad(to_hex(p.project_id), 32, '0')::uuid = t.project_id)
                    JOIN department d ON d.department_id = p.department_id
                    WHERE t.status = 'PUBLISHED' AND d.status = 'ACTIVE'
                      AND 1 - (e.embedding <=> CAST(:vector AS vector)) >= :minSimilarity
                    ORDER BY e.embedding <=> CAST(:vector AS vector), t.ticket_id
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<SolutionRow> nearest(
            @Param("vector") String vector, @Param("minSimilarity") double minSimilarity, @Param("limit") int limit);

    // The `nearest` query narrowed to the Department of one Project (AC-011.1, AC-011.3); returns ticket.public_id.
    @Query(
            value =
                    """
                    SELECT t.public_id AS "ticketId", t.symptoms AS symptoms, t.root_cause AS "rootCause",
                           t.resolution_steps AS "resolutionSteps", t.workaround AS workaround,
                           t.scope AS "scopeOfApplicability",
                           1 - (e.embedding <=> CAST(:vector AS vector)) AS similarity
                    FROM ticket_embedding e JOIN ticket t ON t.ticket_id = e.ticket_id
                    JOIN project p ON (p.public_id = t.project_id
                         OR lpad(to_hex(p.project_id), 32, '0')::uuid = t.project_id)
                    JOIN department d ON d.department_id = p.department_id
                    WHERE t.status = 'PUBLISHED' AND d.status = 'ACTIVE'
                      AND d.department_id = (SELECT department_id FROM project WHERE project_id = :projectId)
                      AND 1 - (e.embedding <=> CAST(:vector AS vector)) >= :minSimilarity
                    ORDER BY e.embedding <=> CAST(:vector AS vector), t.ticket_id
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<TicketSolutionRow> nearestInProjectDepartment(
            @Param("projectId") long projectId,
            @Param("vector") String vector,
            @Param("minSimilarity") double minSimilarity,
            @Param("limit") int limit);

    interface TicketSolutionRow extends SolutionRow {
        UUID getTicketId();
    }

    interface SolutionRow {
        String getSymptoms();

        String getRootCause();

        String getResolutionSteps();

        String getWorkaround();

        String getScopeOfApplicability();

        double getSimilarity();
    }
}
