package vn.tsolve.ticket.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.repository.ProjectAccessRepository;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * The import's Project access from {@code project_membership} (BUG-300). It is the bean whenever {@code
 * TSOLVE_STUB_PROJECTS} is empty — staging and production; {@link StubProjectAccess} answers only when a test or a
 * local demo sets that list.
 */
@Service
@ConditionalOnExpression("'${tsolve.stub.projects:}'.isBlank()")
public class MembershipProjectAccess implements ProjectAccess {
    private final ProjectAccessRepository repository;

    public MembershipProjectAccess(ProjectAccessRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean managesActive(String user, UUID projectId) {
        // the import passes Authentication.getName(), which is the account id
        return projectId != null
                && accountId(user)
                        .map(account -> repository.managesActive(account, projectId))
                        .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID projectId) {
        return projectId != null && repository.isActive(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> jiraProjectKey(UUID projectId) {
        return projectId == null ? Optional.empty() : repository.jiraProjectKey(projectId);
    }

    private static Optional<UUID> accountId(String user) {
        if (user == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(user));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
