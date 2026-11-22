package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.service.TrackerProjectService;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.DepartmentProjectOptionResponse;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.repository.DepartmentRepository;
import vn.tsolve.workspace.repository.DepartmentRepository.Current;
import vn.tsolve.workspace.repository.DepartmentRepository.ProjectOption;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {
    @Mock
    private DepartmentRepository repository;

    @Mock
    private AuditService audit;

    @Mock
    private TenantScope scope;

    @Mock
    private TrackerProjectService tracker;

    // No stubs: a null TransactionStatus is enough for TransactionTemplate over a mock manager.
    @Mock
    private PlatformTransactionManager transactions;

    private DepartmentServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DepartmentServiceImpl(repository, audit, scope, tracker, transactions);
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
    private static final UUID OPS_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID SUP_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final ProjectOption OPS = new ProjectOption(7L, OPS_ID, "OPS");
    private static final ProjectOption SUP = new ProjectOption(8L, SUP_ID, "SUP");

    private static Current current(String name) {
        return new Current(42L, name, null, null);
    }

    private static Current withDefault(ProjectOption project) {
        return new Current(42L, "IT", project.projectId(), project.jiraProjectKey());
    }

    /** The department row as both the pre-read and the locked read see it. */
    private void stored(Current row) {
        when(repository.findActive(ID)).thenReturn(Optional.of(row));
        when(repository.lockActive(ID)).thenReturn(Optional.of(row));
    }

    private static UpdateDepartmentRequest request(String name) {
        return new UpdateDepartmentRequest(name, null);
    }

    private static TransactionDefinition writeTransaction() {
        return argThat(definition -> definition != null && !definition.isReadOnly());
    }

    @Test
    void stripsNameAndRenamesDepartmentAndSpace_AC_003_3() {
        stored(current("IT"));
        when(repository.renameSpace(42L, "IT Support")).thenReturn(UUID.randomUUID());

        var result = service.update(ID, request("  IT Support  "));

        verify(repository).renameDepartment(42L, "IT Support");
        verify(repository).renameSpace(42L, "IT Support");
        verify(repository, never()).setDefaultProject(anyLong(), any());
        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT Support");
    }

    @Test
    void recordsRenameInAudit_AC_003_5() {
        UUID spaceId = UUID.randomUUID();
        stored(current("IT"));
        when(repository.renameSpace(42L, "IT Support")).thenReturn(spaceId);

        service.update(ID, request("IT Support"));

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
        stored(current("IT"));

        var result = service.update(ID, request("  IT "));

        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT");
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(repository, never()).renameSpace(anyLong(), anyString());
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verifyNoInteractions(audit, tracker);
    }

    @Test
    void readsActiveDepartment_AC_003_1() {
        when(repository.findActive(ID)).thenReturn(Optional.of(current("IT")));

        var result = service.get(ID);

        assertThat(result.departmentId()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("IT");
    }

    @Test
    void readsDefaultProjectAndActiveProjects_AC_012_1_AC_012_3() {
        when(repository.findActive(ID)).thenReturn(Optional.of(withDefault(OPS)));
        when(repository.activeProjects(42L)).thenReturn(List.of(OPS, SUP));

        var result = service.get(ID);

        assertThat(result.defaultProject()).isEqualTo(new DepartmentProjectOptionResponse(OPS_ID, "OPS"));
        assertThat(result.projects())
                .containsExactly(
                        new DepartmentProjectOptionResponse(OPS_ID, "OPS"),
                        new DepartmentProjectOptionResponse(SUP_ID, "SUP"));

        // The repository reads a default that is not Active as none (AC-012.3).
        when(repository.findActive(ID)).thenReturn(Optional.of(current("IT")));
        when(repository.activeProjects(42L)).thenReturn(List.of());
        assertThat(service.get(ID).defaultProject()).isNull();
        assertThat(service.get(ID).projects()).isEmpty();
    }

    @Test
    void throwsNotFoundWhenNoActiveDepartment_AC_003_2() {
        when(repository.findActive(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID)))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verifyNoInteractions(audit, tracker);
    }

    @Test
    void recordsNoAuditWhenSpaceRenameFails_AC_003_17() {
        stored(current("IT"));
        var failure = new IllegalStateException("no Space");
        when(repository.renameSpace(42L, "HR")).thenThrow(failure);

        assertThatThrownBy(() -> service.update(ID, request("HR"))).isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @Test
    void failsBeforeAnyRepositoryCallWithoutWorkspace_AC_003_21_AC_012_14() {
        var failure = new IllegalStateException("no Workspace is bound");
        when(scope.currentWorkspaceId()).thenThrow(failure);

        assertThatThrownBy(() -> service.get(ID)).isSameAs(failure);
        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID)))
                .isSameAs(failure);
        verifyNoInteractions(repository, audit, tracker, transactions);
    }

    @Test
    void keepsTheCurrentDefaultWithoutJira_AC_012_10() {
        stored(withDefault(OPS));
        when(repository.lockActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));

        var result = service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID));

        assertThat(result.defaultProject()).isEqualTo(new DepartmentProjectOptionResponse(OPS_ID, "OPS"));
        verify(repository, never()).findActiveProject(anyLong(), any());
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verifyNoInteractions(tracker, audit);
    }

    @Test
    void neverStoresAnUncheckedDefaultAfterAConcurrentChange_AC_012_7_BUG_315() {
        // The pre-read sees OPS, so no Jira check; another save sets SUP before this one takes the lock.
        when(repository.findActive(ID)).thenReturn(Optional.of(withDefault(OPS)));
        when(repository.lockActive(ID)).thenReturn(Optional.of(withDefault(SUP)));
        when(repository.lockActiveProject(42L, SUP_ID)).thenReturn(Optional.of(SUP));

        var result = service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID));

        assertThat(result.defaultProject()).isEqualTo(new DepartmentProjectOptionResponse(SUP_ID, "SUP"));
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verifyNoInteractions(tracker, audit);
    }

    @Test
    void sendsNoJiraCallForNullWithoutDefault_AC_012_10() {
        stored(current("IT"));

        var result = service.update(ID, request("IT"));

        assertThat(result.defaultProject()).isNull();
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verifyNoInteractions(tracker, audit);
    }

    @Test
    void refusesAProjectOutsideTheDepartmentBeforeJira_AC_012_5() {
        when(repository.findActive(ID)).thenReturn(Optional.of(current("IT")));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("HR", OPS_ID)))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("MSG04");
                    assertThat(error.getParams()).isEmpty();
                    assertThat(error.getField()).isEqualTo("defaultProjectId");
                });
        verify(repository, never()).lockActive(any());
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(transactions, never()).getTransaction(writeTransaction());
        verifyNoInteractions(tracker, audit);
    }

    @ParameterizedTest
    @CsvSource({
        "NOT_READY, MSG157, project_name, OPS",
        "DENIED, MSG156, tracker_name, Jira",
        "UNAVAILABLE, MSG156, tracker_name, Jira"
    })
    void mapsEachFailedCheckToItsFieldError_AC_012_8_AC_012_9(
            ChatbotProjectCheck outcome, String code, String param, String value) {
        when(repository.findActive(ID)).thenReturn(Optional.of(current("IT")));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(tracker.checkChatbotProject("OPS")).thenReturn(outcome);

        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("HR", OPS_ID)))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(code);
                    assertThat(error.getParams()).isEqualTo(Map.of(param, value));
                    assertThat(error.getField()).isEqualTo("defaultProjectId");
                });
        verify(repository, never()).lockActive(any());
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verify(transactions, never()).getTransaction(writeTransaction());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesAProjectGoneWhenTheWriteStarts_AC_012_13() {
        stored(current("IT"));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(tracker.checkChatbotProject("OPS")).thenReturn(ChatbotProjectCheck.READY);
        when(repository.lockActiveProject(42L, OPS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ID, new UpdateDepartmentRequest("HR", OPS_ID)))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG04");
                    assertThat(error.getField()).isEqualTo("defaultProjectId");
                });
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(repository, never()).setDefaultProject(anyLong(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void storesTheCheckedProjectAndKeepsTheName_AC_012_4_AC_012_12_AC_012_2() {
        stored(current("IT"));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(tracker.checkChatbotProject("OPS")).thenReturn(ChatbotProjectCheck.READY);
        when(repository.lockActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(repository.activeProjects(42L)).thenReturn(List.of(OPS, SUP));

        var result = service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID));

        verify(repository).setDefaultProject(42L, 7L);
        verify(repository, never()).renameDepartment(anyLong(), anyString());
        verify(repository, never()).renameSpace(anyLong(), anyString());
        assertThat(result.name()).isEqualTo("IT");
        assertThat(result.defaultProject()).isEqualTo(new DepartmentProjectOptionResponse(OPS_ID, "OPS"));
        assertThat(result.projects()).hasSize(2);
    }

    @Test
    void auditsDefaultProjectOnlyWhenItChanged_AC_012_11() {
        stored(current("IT"));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(tracker.checkChatbotProject("OPS")).thenReturn(ChatbotProjectCheck.READY);
        when(repository.lockActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));

        service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", "IT");
        before.put("defaultProject", null);
        verify(audit)
                .record(
                        AuditAction.DEPARTMENT_CHANGE,
                        "DEPARTMENT",
                        ID,
                        Map.of(
                                "operation",
                                "UPDATE",
                                "before",
                                before,
                                "after",
                                Map.of(
                                        "name",
                                        "IT",
                                        "defaultProject",
                                        Map.of("projectId", OPS_ID, "jiraProjectKey", "OPS"))));
    }

    @Test
    void auditsNameSpaceAndClearedDefault_AC_012_6_AC_012_11() {
        UUID spaceId = UUID.randomUUID();
        stored(withDefault(SUP));
        when(repository.renameSpace(42L, "HR")).thenReturn(spaceId);

        var result = service.update(ID, request("HR"));

        verify(repository).setDefaultProject(42L, null);
        assertThat(result.defaultProject()).isNull();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "HR");
        after.put("defaultProject", null);
        verify(audit)
                .record(
                        AuditAction.DEPARTMENT_CHANGE,
                        "DEPARTMENT",
                        ID,
                        Map.of(
                                "operation",
                                "UPDATE",
                                "before",
                                Map.of(
                                        "name",
                                        "IT",
                                        "defaultProject",
                                        Map.of("projectId", SUP_ID, "jiraProjectKey", "SUP")),
                                "after",
                                after,
                                "spaceId",
                                spaceId));
        verifyNoInteractions(tracker);
    }

    @Test
    void checksJiraBeforeTheWriteTransaction_AC_012_7() {
        stored(current("IT"));
        when(repository.findActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));
        when(tracker.checkChatbotProject("OPS")).thenReturn(ChatbotProjectCheck.READY);
        when(repository.lockActiveProject(42L, OPS_ID)).thenReturn(Optional.of(OPS));

        service.update(ID, new UpdateDepartmentRequest("IT", OPS_ID));

        InOrder order = inOrder(repository, tracker, transactions);
        order.verify(repository).findActiveProject(42L, OPS_ID);
        order.verify(tracker).checkChatbotProject("OPS");
        order.verify(transactions).getTransaction(writeTransaction());
        order.verify(repository).lockActive(ID);
    }
}
