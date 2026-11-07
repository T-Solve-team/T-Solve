package vn.tsolve.search.query.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only; writes use the repository's native insert. */
@Entity
@Immutable
@Table(name = "search_log")
public class SearchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "search_log_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, columnDefinition = "text")
    private String query;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SearchLog() {}
}
