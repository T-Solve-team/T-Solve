package vn.tsolve.triage.service.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.repository.ManagedProjectRepository;
import vn.tsolve.triage.service.ManagedProjects;

/** The caller's managed Projects from {@code project_membership}; a user name that is not an account id has none. */
@Service
public class MembershipManagedProjects implements ManagedProjects {
    private final ManagedProjectRepository repository;

    public MembershipManagedProjects(ManagedProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagedProject> of(String user) {
        UUID accountId = accountId(user);
        return accountId == null ? List.of() : repository.managedBy(accountId);
    }

    private static UUID accountId(String user) {
        if (user == null) {
            return null;
        }
        try {
            return UUID.fromString(user);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
