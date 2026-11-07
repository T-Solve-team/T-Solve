package vn.tsolve.search.query.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.query.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, UUID> {
    @Transactional
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO search_log (user_id, department_id, query, channel, result_count, is_fallback)
                    VALUES (:userId, (SELECT department_id FROM department
                        WHERE public_id = CAST(:departmentPublicId AS uuid)),
                        :query, :channel, :resultCount, :isFallback)
                    """,
            nativeQuery = true)
    void log(
            @Param("userId") UUID userId,
            @Param("departmentPublicId") String departmentPublicId,
            @Param("query") String query,
            @Param("channel") String channel,
            @Param("resultCount") int resultCount,
            @Param("isFallback") boolean isFallback);
}
