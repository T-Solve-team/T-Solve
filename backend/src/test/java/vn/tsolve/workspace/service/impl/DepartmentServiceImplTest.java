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
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
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
}
