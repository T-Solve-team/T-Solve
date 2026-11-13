package vn.tsolve.common.session.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.CurrentUserResponse;
import vn.tsolve.common.session.dto.DepartmentRef;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.UserSummaryResponse;
import vn.tsolve.common.session.lookup.DepartmentLookup;
import vn.tsolve.common.session.lookup.ManagedSpaceLookup;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.session.service.SessionService;

/** Lookups are optional: until the owning module provides a bean, its part of the answer is empty. */
@Service
public class SessionServiceImpl implements SessionService {
    private final ObjectProvider<DepartmentLookup> departments;
    private final ObjectProvider<ProjectMembershipLookup> projects;
    private final ObjectProvider<ManagedSpaceLookup> spaces;
    private final ObjectProvider<WorkspaceStatusLookup> workspace;

    public SessionServiceImpl(
            ObjectProvider<DepartmentLookup> departments,
            ObjectProvider<ProjectMembershipLookup> projects,
            ObjectProvider<ManagedSpaceLookup> spaces,
            ObjectProvider<WorkspaceStatusLookup> workspace) {
        this.departments = departments;
        this.projects = projects;
        this.spaces = spaces;
        this.workspace = workspace;
    }

    @Override
    public CurrentUserResponse getMe() {
        var principal = TSolveUser.current().orElseThrow();
        var id = principal.id();
        var department = ask(departments, lookup -> lookup.findDepartment(id), Optional.<DepartmentRef>empty())
                .orElse(null);
        var memberships = ask(projects, lookup -> lookup.findProjects(id), List.<ProjectMembership>of());
        var all = new LinkedHashMap<UUID, DepartmentRef>();
        if (department != null) {
            all.putIfAbsent(department.departmentId(), department);
        }
        memberships.forEach(
                p -> all.putIfAbsent(p.departmentId(), new DepartmentRef(p.departmentId(), p.departmentName())));
        return new CurrentUserResponse(
                new UserSummaryResponse(id, principal.fullName(), principal.email()),
                principal.roles().stream().sorted().toList(),
                department,
                List.copyOf(all.values()),
                memberships,
                ask(spaces, lookup -> lookup.findManagedSpaces(id), List.of()),
                ask(workspace, WorkspaceStatusLookup::isCompanyInformationEntered, false));
    }

    private static <L, R> R ask(ObjectProvider<L> provider, Function<L, R> call, R none) {
        var lookup = provider.getIfAvailable();
        return lookup == null ? none : call.apply(lookup);
    }
}
