package vn.tsolve.triage.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.tsolve.triage.entity.SuggestionResultRecord;

public interface SuggestionResultRecordRepository
        extends JpaRepository<SuggestionResultRecord, UUID>, JpaSpecificationExecutor<SuggestionResultRecord> {

    Optional<SuggestionResultRecord> findByJiraIssueKeyAndProjectIdIn(String jiraIssueKey, Collection<UUID> projectIds);

    /** The rows of {@code filter}; the caller passes a non-empty Project set (an empty one means no rows at all). */
    default Page<SuggestionResultRecord> search(HistoryFilter filter, Pageable pageable) {
        return findAll(matching(filter), pageable);
    }

    static Specification<SuggestionResultRecord> matching(HistoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(root.get("projectId").in(filter.projectIds()));
            if ("NONE".equals(filter.answer())) {
                where.add(cb.isNull(root.get("reporterAnswer")));
            } else if (filter.answer() != null) {
                where.add(cb.equal(root.get("reporterAnswer"), filter.answer()));
            }
            if (filter.start() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.start()));
                where.add(cb.lessThan(root.get("createdAt"), filter.end()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
