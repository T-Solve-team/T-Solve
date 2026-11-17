package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.ProjectRole;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.repository.ScopeTicketRepository.ActiveDepartment;

class MembershipVisibleScopeTest {
    private static final TSolveUser USER =
            new TSolveUser(UUID.randomUUID(), "resolver@example.test", "Resolver", Set.of(Role.TICKET_RESOLVER));

    private final ScopeTicketRepository repository = mock(ScopeTicketRepository.class);
    private final ScopeAccessService access = mock(ScopeAccessService.class);
    private final ProjectMembershipLookup memberships = mock(ProjectMembershipLookup.class);
    private final MembershipVisibleScope scope = new MembershipVisibleScope(repository, access, memberships);

    @Test
    void departmentsAreTheActiveOnesIdentityGrants_AC_012_1() {
        UUID it = UUID.randomUUID();
        UUID hr = UUID.randomUUID();
        UUID ops = UUID.randomUUID();
        when(repository.activeDepartmentRefs())
                .thenReturn(List.of(department(1L, it), department(2L, hr), department(3L, ops)));
        when(access.canAccessDepartment(USER.id(), it)).thenReturn(true);
        when(access.canAccessDepartment(USER.id(), ops)).thenReturn(true);

        assertThat(scope.of(USER).departmentIds()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void managedProjectsAreTheProjectManagerMemberships_AC_012_2() {
        UUID managed = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(memberships.findProjects(USER.id()))
                .thenReturn(List.of(
                        membership(managed, ProjectRole.PROJECT_MANAGER), membership(member, ProjectRole.MEMBER)));
        when(repository.projectIds(Set.of(managed))).thenReturn(Set.of(7L));

        assertThat(scope.of(USER).managedProjectIds()).containsExactly(7L);
        verify(repository).projectIds(Set.of(managed));
    }

    @Test
    void writerIdIsTheAccountId_AC_012_3() {
        assertThat(scope.of(USER).writerId()).contains(USER.id());
    }

    @Test
    void noGrantedDepartmentAndNoManagedProjectLeavesOnlyTheWriter_AC_012_4() {
        when(repository.activeDepartmentRefs()).thenReturn(List.of(department(1L, UUID.randomUUID())));
        when(memberships.findProjects(USER.id()))
                .thenReturn(List.of(membership(UUID.randomUUID(), ProjectRole.MEMBER)));

        var answer = scope.of(USER);

        assertThat(answer.departmentIds()).isEmpty();
        assertThat(answer.managedProjectIds()).isEmpty();
        assertThat(answer.writerId()).contains(USER.id());
        verify(repository, never()).projectIds(any());
    }

    @Test
    void readsTheDatabaseOnEveryCall_AC_012_5() {
        scope.of(USER);
        scope.of(USER);

        verify(repository, times(2)).activeDepartmentRefs();
        verify(memberships, times(2)).findProjects(USER.id());
    }

    private static ActiveDepartment department(long id, UUID publicId) {
        return new ActiveDepartment() {
            @Override
            public Long getDepartmentId() {
                return id;
            }

            @Override
            public UUID getPublicId() {
                return publicId;
            }
        };
    }

    private static ProjectMembership membership(UUID projectId, ProjectRole role) {
        return new ProjectMembership(projectId, "KEY", UUID.randomUUID(), "Department", role);
    }
}
