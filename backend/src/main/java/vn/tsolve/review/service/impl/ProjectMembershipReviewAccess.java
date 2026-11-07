package vn.tsolve.review.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.review.repository.ProjectMembershipRepository;
import vn.tsolve.review.repository.ProjectMembershipRepository.ProjectRow;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;

/**
 * Review's Project scope from {@code project_membership} in the bound tenant database. A missing or inactive Account
 * gets empty, so the controllers deny. Tickets refer to a Project by its public id or by the legacy hex form of its
 * numeric id, so each Project is keyed by both.
 */
@Service
public class ProjectMembershipReviewAccess implements ReviewWorkspaceAccess {

    private final ProjectMembershipRepository memberships;

    public ProjectMembershipReviewAccess(ProjectMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<UUID>> managedProjectIds(UUID accountId) {
        return rows(accountId).map(rows -> rows.stream()
                .filter(ProjectRow::getManaged)
                .flatMap(row -> List.of(row.getPublicId(), row.getLegacyId()).stream())
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Map<UUID, ProjectAccess>> readableProjects(UUID accountId) {
        return rows(accountId).map(rows -> {
            Map<UUID, ProjectAccess> projects = new HashMap<>();
            for (ProjectRow row : rows) {
                ProjectAccess access = new ProjectAccess(
                        new Project(row.getPublicId(), row.getProjectKey()), row.getDepartmentId(), row.getManaged());
                projects.put(row.getPublicId(), access);
                projects.put(row.getLegacyId(), access);
            }
            return Map.copyOf(projects);
        });
    }

    private Optional<List<ProjectRow>> rows(UUID accountId) {
        if (accountId == null || !memberships.isActiveAccount(accountId)) {
            return Optional.empty();
        }
        return Optional.of(memberships.activeProjects(accountId));
    }
}
