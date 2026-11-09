package vn.tsolve.review.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewExpiryRepository {
    private final JdbcClient jdbc;

    public ReviewExpiryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> expireOverdue(LocalDate today) {
        // Department readers currently recognize only ACTIVE; the schema has no deleted-state enum.
        // trg_ticket_updated_at maintains updated_at, as it does for review decisions.
        return jdbc.sql(
                        """
                        UPDATE ticket t
                        SET status_before_expiry = t.status, status = 'EXPIRED', expired_at = statement_timestamp()
                        FROM project p JOIN department d ON d.department_id = p.department_id
                        WHERE t.project_id = p.public_id
                          AND d.status = 'ACTIVE'
                          AND t.status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED')
                          AND t.review_due_date < :today
                        RETURNING t.project_id
                        """)
                .param("today", today)
                .query(UUID.class)
                .list();
    }
}
