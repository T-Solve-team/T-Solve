package vn.tsolve.project.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByJiraProjectKeyAndStatus(String jiraProjectKey, ProjectStatus status);
}
