package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.repository.DepartmentRepository;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {
    @Mock
    private DepartmentRepository repository;

    @Mock
    private AuditService audit;

    @Mock
    private TenantScope scope;

    private DepartmentServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DepartmentServiceImpl(repository, audit, scope);
    }

    @Test
    void stripsNameCreatesSpaceAndRecordsCreation_AC_001_1_AC_001_3() {
        UUID departmentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(repository.insertDepartment("IT Support"))
                .thenReturn(Optional.of(new DepartmentRepository.Created(42L, departmentId)));
        when(repository.insertSpace(42L, "IT Support")).thenReturn(spaceId);

        var result = service.create(new CreateDepartmentRequest("  IT Support  "));

        verify(repository).insertDepartment("IT Support");
        verify(repository).insertSpace(42L, "IT Support");
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.name()).isEqualTo("IT Support");
        verify(audit)
                .record(
                        AuditAction.DEPARTMENT_CHANGE,
                        "DEPARTMENT",
                        departmentId,
                        Map.of("operation", "CREATE", "name", "IT Support", "spaceId", spaceId));
        verifyNoMoreInteractions(audit);
    }

    @Test
    void refusesDuplicateWithoutSpaceOrAudit_AC_001_7() {
        when(repository.insertDepartment("IT Support")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateDepartmentRequest("IT Support")))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG43");
                    assertThat(error.getField()).isEqualTo("name");
                });
        verify(repository, never()).insertSpace(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void propagatesSpaceFailureWithoutAudit_AC_001_11() {
        when(repository.insertDepartment("IT Support"))
                .thenReturn(Optional.of(new DepartmentRepository.Created(42L, UUID.randomUUID())));
        var failure = new IllegalStateException("space insert failed");
        when(repository.insertSpace(42L, "IT Support")).thenThrow(failure);

        assertThatThrownBy(() -> service.create(new CreateDepartmentRequest("IT Support")))
                .isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @Test
    void failsBeforeWritesWithoutWorkspace_AC_001_15() {
        var failure = new IllegalStateException("no Workspace is bound");
        when(scope.currentWorkspaceId()).thenThrow(failure);

        assertThatThrownBy(() -> service.create(new CreateDepartmentRequest("IT Support")))
                .isSameAs(failure);
        verifyNoInteractions(repository, audit);
    }

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    @Test
    void stripsNameAndRenamesDepartmentAndSpace_AC_003_3() {
        when(repository.lockActive(ID)).thenReturn(Optional.of(new DepartmentRepository.Current(42L, "IT")));
        when(repository.renameSpace(42L, "IT Support")).thenReturn(UUID.randomUUID());

        var result = service.update(ID, new UpdateDepartmentRequest("  IT Support  "));

        verify(repository).renameDepartment(42L, "IT Support");
        verify(repository).renameSpace(42L, "IT Support");
        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT Support");
    }

    @Test
    void recordsRenameInAudit_AC_003_5() {
        UUID spaceId = UUID.randomUUID();
        when(repository.lockActive(ID)).thenReturn(Optional.of(new DepartmentRepository.Current(42L, "IT")));
        when(repository.renameSpace(42L, "IT Support")).thenReturn(spaceId);

        service.update(ID, new UpdateDepartmentRequest("IT Support"));

        verify(audit)
                .record(
                        AuditAction.DEPARTMENT_CHANGE,
                        "DEPARTMENT",
                        ID,
                        Map.of(
                                "operation",
                                "UPDATE",
                                "before",
                                Map.of("name", "IT"),
                                "after",
                                Map.of("name", "IT Support"),
                                "spaceId",
                                spaceId));
        verifyNoMoreInteractions(audit);
    }

    @Test
    void writesNothingWhenNameIsUnchanged_AC_003_14() {
        when(repository.lockActive(ID)).thenReturn(Optional.of(new DepartmentRepository.Current(42L, "IT")));

        var result = service.update(ID, new UpdateDepartmentRequest("  IT "));

        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT");
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(repository, never()).renameSpace(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void readsActiveDepartment_AC_003_1() {
        when(repository.findActive(ID)).thenReturn(Optional.of(new DepartmentRepository.Current(42L, "IT")));

        var result = service.get(ID);

        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT");
    }

    @Test
    void throwsNotFoundWhenNoActiveDepartment_AC_003_2() {
        when(repository.findActive(ID)).thenReturn(Optional.empty());
        when(repository.lockActive(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("IT")))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void recordsNoAuditWhenSpaceRenameFails_AC_003_17() {
        when(repository.lockActive(ID)).thenReturn(Optional.of(new DepartmentRepository.Current(42L, "IT")));
        var failure = new IllegalStateException("no Space");
        when(repository.renameSpace(42L, "HR")).thenThrow(failure);

        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("HR")))
                .isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @Test
    void failsBeforeAnyRepositoryCallWithoutWorkspace_AC_003_21() {
        var failure = new IllegalStateException("no Workspace is bound");
        when(scope.currentWorkspaceId()).thenThrow(failure);

        assertThatThrownBy(() -> service.get(ID)).isSameAs(failure);
        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("IT")))
                .isSameAs(failure);
        verifyNoInteractions(repository, audit);
    }
}
