package vn.tsolve.search.scope.service.impl;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.ProjectRole;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.service.VisibleScope;

/**
 * The visible scope from the Workspace database (STORY-012). Departments use identity's own access rule, so Ticket
 * Detail, Spaces and search agree on who belongs to a Department. Read on every call: a membership change applies
 * from the next request (AC-012.5).
 */
@Component
@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' != 'all'")
public class MembershipVisibleScope implements VisibleScope {
    private final ScopeTicketRepository repository;
    private final ScopeAccessService access;
    private final ProjectMembershipLookup memberships;

    public MembershipVisibleScope(
            ScopeTicketRepository repository, ScopeAccessService access, ProjectMembershipLookup memberships) {
        this.repository = repository;
        this.access = access;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Scope of(TSolveUser user) {
        UUID accountId = user.id();
        Set<Long> departments = repository.activeDepartmentRefs().stream()
                .filter(department -> access.canAccessDepartment(accountId, department.getPublicId()))
                .map(ScopeTicketRepository.ActiveDepartment::getDepartmentId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> managed = memberships.findProjects(accountId).stream()
                .filter(membership -> membership.roleInProject() == ProjectRole.PROJECT_MANAGER)
                .map(ProjectMembership::projectId)
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> managedProjects = managed.isEmpty() ? Set.of() : repository.projectIds(managed);
        return new Scope(departments, managedProjects, Optional.of(accountId));
    }
}
