package vn.tsolve.common.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.CurrentUserResponse;
import vn.tsolve.common.session.dto.DepartmentRef;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.ProjectRole;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.DepartmentLookup;
import vn.tsolve.common.session.lookup.ManagedSpaceLookup;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.session.service.impl.SessionServiceImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final DepartmentRef D1 = new DepartmentRef(UUID.randomUUID(), "Engineering");
    private static final DepartmentRef D2 = new DepartmentRef(UUID.randomUUID(), "Finance");
    private static final DepartmentRef D3 = new DepartmentRef(UUID.randomUUID(), "Support");

    @Mock
    private ObjectProvider<DepartmentLookup> departmentProvider;

    @Mock
    private ObjectProvider<ProjectMembershipLookup> projectProvider;

    @Mock
    private ObjectProvider<ManagedSpaceLookup> spaceProvider;

    @Mock
    private ObjectProvider<WorkspaceStatusLookup> workspaceProvider;

    @Mock
    private DepartmentLookup departmentLookup;

    @Mock
    private ProjectMembershipLookup projectLookup;

    @Mock
    private ManagedSpaceLookup spaceLookup;

    @Mock
    private WorkspaceStatusLookup workspaceLookup;

    private SessionServiceImpl service;

    @BeforeEach
    void signIn() {
        signInWith(Set.of(Role.ADMIN, Role.TICKET_RESOLVER));
        service = new SessionServiceImpl(departmentProvider, projectProvider, spaceProvider, workspaceProvider);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signInWith(Set<Role> roles) {
        var user = new TSolveUser(USER_ID, "ada@example.test", "Ada Lovelace", roles);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        SecurityContextHolder.setContext(context);
    }

    private void allLookupsPresent() {
        when(departmentProvider.getIfAvailable()).thenReturn(departmentLookup);
        when(projectProvider.getIfAvailable()).thenReturn(projectLookup);
        when(spaceProvider.getIfAvailable()).thenReturn(spaceLookup);
        when(workspaceProvider.getIfAvailable()).thenReturn(workspaceLookup);
        when(departmentLookup.findDepartment(USER_ID)).thenReturn(Optional.empty());
        when(projectLookup.findProjects(USER_ID)).thenReturn(List.of());
        when(spaceLookup.findManagedSpaces(USER_ID)).thenReturn(List.of());
        when(workspaceLookup.isCompanyInformationEntered()).thenReturn(false);
    }

    private static ProjectMembership projectIn(DepartmentRef department, String key) {
        return new ProjectMembership(
                UUID.randomUUID(), key, department.departmentId(), department.name(), ProjectRole.MEMBER);
    }

    @Test
    void buildsUserAndRolesFromPrincipalWithEmployeeFirst_AC_003_1() {
        allLookupsPresent();

        CurrentUserResponse me = service.getMe();

        assertThat(me.user().id()).isEqualTo(USER_ID);
        assertThat(me.user().fullName()).isEqualTo("Ada Lovelace");
        assertThat(me.user().email()).isEqualTo("ada@example.test");
        assertThat(me.roles()).containsExactly(Role.EMPLOYEE, Role.TICKET_RESOLVER, Role.ADMIN);
    }

    @Test
    void takesPartsFromTheFourLookups_AC_003_2() {
        allLookupsPresent();
        var projects = List.of(projectIn(D1, "KAN"));
        var spaces = List.of(new SpaceRef(UUID.randomUUID(), "Engineering wiki"));
        when(departmentLookup.findDepartment(USER_ID)).thenReturn(Optional.of(D1));
        when(projectLookup.findProjects(USER_ID)).thenReturn(projects);
        when(spaceLookup.findManagedSpaces(USER_ID)).thenReturn(spaces);
        when(workspaceLookup.isCompanyInformationEntered()).thenReturn(true);

        CurrentUserResponse me = service.getMe();

        assertThat(me.department()).isEqualTo(D1);
        assertThat(me.projects()).isEqualTo(projects);
        assertThat(me.managedSpaces()).isEqualTo(spaces);
        assertThat(me.workspaceReady()).isTrue();
        verify(departmentLookup).findDepartment(USER_ID);
        verify(projectLookup).findProjects(USER_ID);
        verify(spaceLookup).findManagedSpaces(USER_ID);
        verify(workspaceLookup).isCompanyInformationEntered();
    }

    @Test
    void listsDepartmentThenProjectDepartmentsOnce_AC_003_3_edge15() {
        allLookupsPresent();
        when(departmentLookup.findDepartment(USER_ID)).thenReturn(Optional.of(D1));
        when(projectLookup.findProjects(USER_ID))
                .thenReturn(List.of(projectIn(D1, "A"), projectIn(D2, "B"), projectIn(D2, "C"), projectIn(D3, "D")));

        CurrentUserResponse me = service.getMe();

        assertThat(me.departments()).containsExactly(D1, D2, D3);
    }

    @Test
    void listsOneDepartmentIdOnceEvenWithDifferentNames_AC_003_3() {
        allLookupsPresent();
        var renamed = new DepartmentRef(D1.departmentId(), "Engineering (renamed)");
        when(departmentLookup.findDepartment(USER_ID)).thenReturn(Optional.of(D1));
        when(projectLookup.findProjects(USER_ID)).thenReturn(List.of(projectIn(renamed, "A"), projectIn(D2, "B")));

        CurrentUserResponse me = service.getMe();

        assertThat(me.departments()).containsExactly(D1, D2);
    }

    @Test
    void listsProjectDepartmentsWhenNoDepartment_AC_003_3() {
        allLookupsPresent();
        when(projectLookup.findProjects(USER_ID)).thenReturn(List.of(projectIn(D2, "B"), projectIn(D3, "D")));

        CurrentUserResponse me = service.getMe();

        assertThat(me.department()).isNull();
        assertThat(me.departments()).containsExactly(D2, D3);
    }

    @Test
    void answersEmptyWhenNoLookupBeanExists_AC_003_4_edge14() {
        when(departmentProvider.getIfAvailable()).thenReturn(null);
        when(projectProvider.getIfAvailable()).thenReturn(null);
        when(spaceProvider.getIfAvailable()).thenReturn(null);
        when(workspaceProvider.getIfAvailable()).thenReturn(null);

        CurrentUserResponse me = service.getMe();

        assertThat(me.department()).isNull();
        assertThat(me.departments()).isNotNull().isEmpty();
        assertThat(me.projects()).isNotNull().isEmpty();
        assertThat(me.managedSpaces()).isNotNull().isEmpty();
        assertThat(me.workspaceReady()).isFalse();
    }

    @Test
    void letsLookupFailurePropagate_edge16() {
        allLookupsPresent();
        var failure = new IllegalStateException("lookup is down");
        when(projectLookup.findProjects(USER_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.getMe()).isSameAs(failure);
    }
}
