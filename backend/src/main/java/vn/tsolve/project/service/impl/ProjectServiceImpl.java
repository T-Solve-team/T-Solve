package vn.tsolve.project.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.repository.ProjectRepository;
import vn.tsolve.project.service.ProjectService;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActivePublicIdByJiraProjectKey(String jiraProjectKey) {
        return projectRepository
                .findByJiraProjectKeyAndStatus(jiraProjectKey, ProjectStatus.ACTIVE)
                .map(Project::getPublicId);
    }
}
