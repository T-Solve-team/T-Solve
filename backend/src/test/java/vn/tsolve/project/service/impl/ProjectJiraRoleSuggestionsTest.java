package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.service.JiraAccountLinkLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.JiraProjectRoleResponse;
import vn.tsolve.project.dto.JiraRolePeopleResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse;
import vn.tsolve.project.dto.JiraRolePersonResponse.Status;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.repository.ProjectMemberRepository;
import vn.tsolve.project.repository.ProjectMemberRepository.MemberState;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.dto.TrackerRoleActors.Person;
import vn.tsolve.tracker.service.TrackerProjectService;

/** STORY-011: the Add member popup's "From Jira" tab, read from the Jira project's roles. */
@ExtendWith(MockitoExtension.class)
class ProjectJiraRoleSuggestionsTest {
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID ADDABLE = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID INACTIVE = UUID.randomUUID();
    private static final UUID NOT_RESOLVER = UUID.randomUUID();

    @Mock
    private ProjectMemberRepository repository;

    @Mock
    private AuditService audit;

    @Mock
    private TenantScope scope;

    @Mock
    private TrackerProjectService tracker;

    @Mock
    private JiraAccountLinkLookup links;

    private ProjectMemberServiceImpl service;

    @BeforeEach
    void setup() {
        service = new ProjectMemberServiceImpl(repository, audit, scope, tracker, links);
        var user = new TSolveUser(CALLER, "pm@example.test", "PM", Set.of(Role.PROJECT_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void managedProject() {
        when(repository.findProject(PROJECT))
                .thenReturn(Optional.of(new ProjectSummaryResponse(PROJECT, "DP", "IT", "PM", ProjectStatus.ACTIVE)));
        when(repository.isManager(CALLER, PROJECT)).thenReturn(true);
    }

    @Test
    void listsTheJiraRolesByNameWithoutTheAppRole_AC_011_1() {
        managedProject();
        when(tracker.projectRoles("DP"))
                .thenReturn(Optional.of(List.of(
                        new TrackerRole(10018, "atlassian-addons-project-access"),
                        new TrackerRole(10015, "Member"),
                        new TrackerRole(10014, "Administrator"),
                        new TrackerRole(10016, "viewer"))));

        assertThat(service.jiraRoles(PROJECT))
                .containsExactly(
                        new JiraProjectRoleResponse(10014, "Administrator"),
                        new JiraProjectRoleResponse(10015, "Member"),
                        new JiraProjectRoleResponse(10016, "viewer"));
    }

    @Test
    void classifiesEveryPersonOfTheRoleAndNamesItsGroups_AC_011_2() {
        managedProject();
        when(tracker.roleActors("DP", 10015))
                .thenReturn(Optional.of(new TrackerRoleActors(
                        List.of(
                                new Person("j-unlinked", "zoe"),
                                new Person("j-addable", "An"),
                                new Person("j-member", "Binh"),
                                new Person("j-inactive", "Chi"),
                                new Person("j-not-resolver", "dung")),
                        List.of("it-support-team"))));
        when(links.linkedAccountIds(Set.of("j-unlinked", "j-addable", "j-member", "j-inactive", "j-not-resolver")))
                .thenReturn(Map.of(
                        "j-addable", ADDABLE,
                        "j-member", MEMBER,
                        "j-inactive", INACTIVE,
                        "j-not-resolver", NOT_RESOLVER));
        when(repository.memberStates(PROJECT, Set.of(ADDABLE, MEMBER, INACTIVE, NOT_RESOLVER)))
                .thenReturn(Map.of(
                        ADDABLE, new MemberState(ADDABLE, "An Nguyen", "an@acme.example", true, true, false),
                        MEMBER, new MemberState(MEMBER, "Binh", "binh@acme.example", true, true, true),
                        INACTIVE, new MemberState(INACTIVE, "Chi", "chi@acme.example", false, true, false),
                        NOT_RESOLVER, new MemberState(NOT_RESOLVER, "Dung", "dung@acme.example", true, false, false)));

        JiraRolePeopleResponse people = service.jiraRolePeople(PROJECT, 10015);

        assertThat(people.people())
                .containsExactly(
                        JiraRolePersonResponse.addable("j-addable", "An", ADDABLE, "An Nguyen", "an@acme.example"),
                        JiraRolePersonResponse.notAddable("j-member", "Binh", Status.ALREADY_MEMBER),
                        JiraRolePersonResponse.notAddable("j-inactive", "Chi", Status.NOT_ACTIVE),
                        JiraRolePersonResponse.notAddable("j-not-resolver", "dung", Status.NOT_TICKET_RESOLVER),
                        JiraRolePersonResponse.notAddable("j-unlinked", "zoe", Status.NOT_LINKED));
        assertThat(people.groups()).containsExactly("it-support-team");
    }

    @Test
    void readsNoGroupMemberAndChangesNoMembership_AC_011_3() {
        managedProject();
        when(tracker.roleActors("DP", 10015))
                .thenReturn(Optional.of(new TrackerRoleActors(List.of(), List.of("jira-software-users"))));
        when(links.linkedAccountIds(Set.of())).thenReturn(Map.of());
        when(repository.memberStates(PROJECT, Set.of())).thenReturn(Map.of());

        assertThat(service.jiraRolePeople(PROJECT, 10015).people()).isEmpty();
        verify(repository, never()).addMembers(any(), any());
        verify(repository, never()).removeMember(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void answers503Msg06WhenJiraRolesAreNotAvailable_AC_011_4() {
        managedProject();
        when(tracker.projectRoles("DP")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.jiraRoles(PROJECT))
                .isInstanceOf(ProjectTrackerUnavailableException.class)
                .satisfies(e -> assertThat(((MessageCodeException) e).getCode()).isEqualTo("MSG06"));
    }

    @Test
    void answers503Msg06WhenTheRoleCannotBeRead_AC_011_4() {
        managedProject();
        when(tracker.roleActors("DP", 999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.jiraRolePeople(PROJECT, 999))
                .isInstanceOf(ProjectTrackerUnavailableException.class);
        verifyNoInteractions(links);
    }

    @Test
    void refusesAnotherCallerAndAnUnknownProjectBeforeAnyJiraCall_AC_011_5() {
        when(repository.findProject(PROJECT))
                .thenReturn(Optional.of(new ProjectSummaryResponse(PROJECT, "DP", "IT", "PM", ProjectStatus.ACTIVE)));
        when(repository.isManager(CALLER, PROJECT)).thenReturn(false);
        assertThatThrownBy(() -> service.jiraRoles(PROJECT)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.jiraRolePeople(PROJECT, 10015)).isInstanceOf(AccessDeniedException.class);

        UUID unknown = UUID.randomUUID();
        when(repository.findProject(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.jiraRoles(unknown)).isInstanceOf(NotFoundException.class);

        verify(tracker, never()).projectRoles(anyString());
        verify(tracker, never()).roleActors(anyString(), anyLong());
    }
}
