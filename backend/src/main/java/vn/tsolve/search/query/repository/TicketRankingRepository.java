package vn.tsolve.search.query.repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface TicketRankingRepository extends JpaRepository<SearchTicket, Long> {
    String COLUMNS =
            """
            SELECT t.public_id AS "ticketId", t.title AS "title",
                p.public_id AS "projectId", p.jira_project_key AS "projectName",
                d.public_id AS "departmentId", d.name AS "departmentName",
                t.symptoms AS "symptoms", t.resolution_steps AS "resolutionSteps",
                t.resolved_date AS "resolvedDate",
                CAST(round(100.0 * f.helpful / f.total) AS integer) AS "helpfulRate"
            """;
    String TABLES =
            """
            FROM ticket t
            JOIN project p ON p.public_id = t.project_id
            JOIN department d ON d.department_id = p.department_id
            """;
    String FEEDBACK =
            """
            LEFT JOIN (
                SELECT ticket_id, count(*) AS total,
                    count(*) FILTER (WHERE rating = 'HELPFUL') AS helpful
                FROM ticket_feedback GROUP BY ticket_id
            ) f ON f.ticket_id = t.id
            """;
    String FILTERS =
            """
            WHERE t.status = 'PUBLISHED' AND d.status = 'ACTIVE'
                AND p.department_id IN (:departmentIds)
                AND (CAST(:departmentId AS text) IS NULL OR d.public_id = CAST(:departmentId AS uuid))
                AND (CAST(:source AS text) IS NULL OR t.source = CAST(:source AS text))
                AND (CAST(:resolvedFrom AS text) IS NULL OR t.resolved_date >= CAST(:resolvedFrom AS date))
                AND (CAST(:resolvedTo AS text) IS NULL OR t.resolved_date <= CAST(:resolvedTo AS date))
            """;
    String SIMILARITY = " AND 1 - (e.embedding <=> CAST(:query AS vector)) >= :minSimilarity ";
    String KEYWORD =
            """
             AND (t.title ILIKE :keyword ESCAPE '\\'
                OR t.symptoms ILIKE :keyword ESCAPE '\\'
                OR t.resolution_steps ILIKE :keyword ESCAPE '\\')
            """;

    // ponytail: exact cosine ranking has no HNSW index yet; measure on real data before adding one.
    @Transactional(readOnly = true)
    @Query(
            value = COLUMNS + TABLES + " JOIN ticket_embedding e ON e.ticket_id = t.id " + FEEDBACK + FILTERS
                    + SIMILARITY + " ORDER BY e.embedding <=> CAST(:query AS vector), t.id ",
            countQuery = "SELECT count(*) " + TABLES + " JOIN ticket_embedding e ON e.ticket_id = t.id " + FILTERS
                    + SIMILARITY,
            nativeQuery = true)
    Page<RankedRow> semantic(
            @Param("departmentIds") Set<Long> departmentIds,
            @Param("departmentId") String departmentId,
            @Param("source") String source,
            @Param("resolvedFrom") String resolvedFrom,
            @Param("resolvedTo") String resolvedTo,
            @Param("query") String query,
            @Param("minSimilarity") double minSimilarity,
            Pageable page);

    @Transactional(readOnly = true)
    @Query(
            value = COLUMNS + TABLES + FEEDBACK + FILTERS + KEYWORD
                    + " ORDER BY t.resolved_date DESC NULLS LAST, t.id DESC ",
            countQuery = "SELECT count(*) " + TABLES + FILTERS + KEYWORD,
            nativeQuery = true)
    Page<RankedRow> keyword(
            @Param("departmentIds") Set<Long> departmentIds,
            @Param("departmentId") String departmentId,
            @Param("source") String source,
            @Param("resolvedFrom") String resolvedFrom,
            @Param("resolvedTo") String resolvedTo,
            @Param("keyword") String keyword,
            Pageable page);

    interface RankedRow {
        UUID getTicketId();

        String getTitle();

        UUID getProjectId();

        String getProjectName();

        UUID getDepartmentId();

        String getDepartmentName();

        String getSymptoms();

        String getResolutionSteps();

        Integer getHelpfulRate();

        LocalDate getResolvedDate();
    }
}
