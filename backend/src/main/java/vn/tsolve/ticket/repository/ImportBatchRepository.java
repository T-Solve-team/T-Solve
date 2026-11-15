package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ImportBatch;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    String HISTORY_FROM = """
        from import_batch b
        join project p on (p.public_id = b.project_id or lpad(to_hex(p.project_id), 32, '0')::uuid = b.project_id)
        join department d on d.department_id = p.department_id
        left join app_user u on u.id::text = b.performed_by
        where exists (select 1 from project_membership m
                      where m.project_id = p.public_id and m.user_id = :caller
                        and m.role_in_project = 'PROJECT_MANAGER')
          and (cast(:projectId as uuid) is null or p.public_id = :projectId)
          and (cast(:source as varchar) is null or b.source = :source)
          and (cast(:fromInclusive as timestamptz) is null or b.created_at >= :fromInclusive)
          and (cast(:toExclusive as timestamptz) is null or b.created_at < :toExclusive)
        """;

    @Query(
            value = """
                select b.id as batchId, b.created_at as createdAt, coalesce(u.full_name, b.performed_by) as performedBy,
                       p.public_id as projectId, p.jira_project_key as projectKey,
                       d.public_id as departmentId, d.name as departmentName,
                       b.source as source, b.date_from as dateFrom, b.date_to as dateTo, b.file_name as fileName,
                       b.published_count as publishedCount, b.removed_count as removedCount, b.error_count as errorCount,
                       b.status as status, b.error as error
                """ + HISTORY_FROM + " order by b.created_at desc, b.id desc",
            countQuery = "select count(*) " + HISTORY_FROM,
            nativeQuery = true)
    Page<HistoryRow> findHistory(
            @Param("caller") UUID caller,
            @Param("projectId") UUID projectId,
            @Param("source") String source,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            Pageable page);

    interface HistoryRow {
        long getBatchId();

        Instant getCreatedAt();

        String getPerformedBy();

        UUID getProjectId();

        String getProjectKey();

        UUID getDepartmentId();

        String getDepartmentName();

        String getSource();

        LocalDate getDateFrom();

        LocalDate getDateTo();

        String getFileName();

        int getPublishedCount();

        int getRemovedCount();

        int getErrorCount();

        String getStatus();

        String getError();
    }
}
