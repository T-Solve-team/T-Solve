package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.dto.AddProjectMembersRequest;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.repository.ProjectMemberRepository;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceImplTest {
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Mock
    private ProjectMemberRepository repository;

    @Mock
    private AuditService audit;

    @Mock
    private TenantScope scope;

    private ProjectMemberServiceImpl service;

    @BeforeEach
    void setup() {
        service = new ProjectMemberServiceImpl(repository, audit, scope);
        var user = new TSolveUser(CALLER, "pm@example.test", "PM", Set.of(Role.PROJECT_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void project(ProjectStatus status, boolean manager) {
        when(repository.findProject(PROJECT))
                .thenReturn(Optional.of(new ProjectSummaryResponse(PROJECT, "KEY", "Dept", "PM", status)));
        when(repository.isManager(CALLER, PROJECT)).thenReturn(manager);
    }

    @Test
    void answersNotFoundBeforeManagerCheck_AC_001_4() {
        when(repository.findProject(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(PROJECT))
                .isInstanceOfSatisfying(
                        NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MSG04"));
        verify(repository, never()).isManager(any(), any());
    }

    @Test
    void refusesCallerTheManagerQueryRejects_AC_001_3() {
        // The query itself (Active account, PM role, PM membership row) is proven against a database in the IT.
        project(ProjectStatus.ACTIVE, false);

        assertThatThrownBy(() -> service.getProject(PROJECT)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addMembers(PROJECT, new AddProjectMembersRequest(List.of(A))))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).addMembers(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesSignedOutCaller_AC_001_3() {
        SecurityContextHolder.clearContext();
        when(repository.findProject(PROJECT))
                .thenReturn(
                        Optional.of(new ProjectSummaryResponse(PROJECT, "KEY", "Dept", "PM", ProjectStatus.ACTIVE)));

        assertThatThrownBy(() -> service.getProject(PROJECT)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsQueryLongerThan100AfterTrim_AC_002_3() {
        project(ProjectStatus.ACTIVE, true);

        assertThatThrownBy(() -> service.searchCandidates(PROJECT, "x".repeat(101)))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("MSG02");
                });
        verify(repository, never()).candidates(any(), any());

        service.searchCandidates(PROJECT, "  " + "x".repeat(100) + "  ");
        verify(repository).candidates(PROJECT, "x".repeat(100));
    }

    @Test
    void searchesWithEmptyTextWhenQueryMissingOrBlank_AC_002_2() {
        project(ProjectStatus.ACTIVE, true);

        service.searchCandidates(PROJECT, null);
        service.searchCandidates(PROJECT, "   ");

        verify(repository, times(2)).candidates(PROJECT, "");
    }

    @Test
    void rejectsDuplicateIdsAndAddsNobody_AC_002_6() {
        project(ProjectStatus.ACTIVE, true);

        assertThatThrownBy(() -> service.addMembers(PROJECT, new AddProjectMembersRequest(List.of(A, B, A))))
                .isInstanceOfSatisfying(
                        FieldMessageCodeException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(400));
        verify(repository, never()).addMembers(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesIneligibleIdAndAddsNobody_AC_002_7() {
        project(ProjectStatus.ACTIVE, true);
        when(repository.lockEligible(PROJECT, List.of(A, B))).thenReturn(1);

        assertThatThrownBy(() -> service.addMembers(PROJECT, new AddProjectMembersRequest(List.of(A, B))))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
        verify(repository, never()).addMembers(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void mapsConcurrentDuplicateInsertToConflictWithoutAudit_AC_002_7() {
        // Design E5: two requests add the same person at once; the loser's insert hits the primary key.
        project(ProjectStatus.ACTIVE, true);
        when(repository.lockEligible(PROJECT, List.of(A))).thenReturn(1);
        doThrow(new DuplicateKeyException("pk")).when(repository).addMembers(PROJECT, List.of(A));

        assertThatThrownBy(() -> service.addMembers(PROJECT, new AddProjectMembersRequest(List.of(A))))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void refusesAddToRemovedProject_AC_002_8() {
        project(ProjectStatus.REMOVED, true);

        assertThatThrownBy(() -> service.addMembers(PROJECT, new AddProjectMembersRequest(List.of(A))))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).addMembers(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void addsMembersRecordsOneAuditInSendOrderAndReturnsList_AC_002_4_AC_002_5() {
        project(ProjectStatus.ACTIVE, true);
        List<UUID> ids = new ArrayList<>(List.of(B, A));
        when(repository.lockEligible(PROJECT, ids)).thenReturn(2);
        var members = List.<ProjectMemberResponse>of();
        when(repository.members(PROJECT)).thenReturn(members);

        assertThat(service.addMembers(PROJECT, new AddProjectMembersRequest(ids)))
                .isSameAs(members);

        var order = inOrder(repository, audit);
        order.verify(repository).addMembers(PROJECT, List.of(B, A));
        order.verify(audit)
                .record(AuditAction.PROJECT_MEMBER_CHANGE, "PROJECT", PROJECT, Map.of("added", List.of(B, A)));
        order.verify(repository).members(PROJECT);
    }

    @Test
    void refusesRemoveWhenNoMemberRowIsDeletedWithoutAudit_AC_003_3() {
        project(ProjectStatus.ACTIVE, true);
        when(repository.removeMember(PROJECT, A)).thenReturn(0);

        assertThatThrownBy(() -> service.removeMember(PROJECT, A))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
        verifyNoInteractions(audit);
    }

    @Test
    void refusesRemoveFromRemovedProjectBeforeDelete_AC_003_4() {
        project(ProjectStatus.REMOVED, true);

        assertThatThrownBy(() -> service.removeMember(PROJECT, A)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).removeMember(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesRemoveByNonManagerBeforeDelete_AC_001_3() {
        project(ProjectStatus.ACTIVE, false);

        assertThatThrownBy(() -> service.removeMember(PROJECT, A)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).removeMember(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void removesMemberRecordsOneAuditAndReturnsList_AC_003_1_AC_003_2() {
        project(ProjectStatus.ACTIVE, true);
        when(repository.removeMember(PROJECT, A)).thenReturn(1);
        var members = List.<ProjectMemberResponse>of();
        when(repository.members(PROJECT)).thenReturn(members);

        assertThat(service.removeMember(PROJECT, A)).isSameAs(members);

        var order = inOrder(repository, audit);
        order.verify(repository).removeMember(PROJECT, A);
        order.verify(audit)
                .record(AuditAction.PROJECT_MEMBER_CHANGE, "PROJECT", PROJECT, Map.of("removed", List.of(A)));
        order.verify(repository).members(PROJECT);
    }
}
