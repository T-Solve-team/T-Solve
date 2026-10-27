package vn.tsolve.tracker.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.tracker.entity.JiraSite;

public interface JiraSiteRepository extends JpaRepository<JiraSite, Long> {

    /** Run as {@code WorkspaceContext.ROOT}: the cloudId is how a Jira App call finds its Workspace. */
    Optional<JiraSite> findByCloudId(String cloudId);
}
