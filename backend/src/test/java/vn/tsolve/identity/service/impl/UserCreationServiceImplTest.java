package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.repository.UserCreationRepository;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.CompanyDomainFallback;
import vn.tsolve.identity.service.WorkspaceEmailRegistry;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

class UserCreationServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID workspace = UUID.randomUUID();
    private final UserCreationRepository repository = mock(UserCreationRepository.class);
    private final WorkspaceEmailRegistry registry = mock(WorkspaceEmailRegistry.class);
    private final TenantScope scope = mock(TenantScope.class);
    private final WorkspaceLookup lookup = mock(WorkspaceLookup.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserListRepository users = mock(UserListRepository.class);
    private UserCreationServiceImpl service;

    @BeforeEach
    void setup() {
        when(scope.currentWorkspaceId()).thenReturn(workspace);
        when(lookup.findById(workspace))
                .thenReturn(Optional.of(
                        new WorkspaceRef(workspace, "site", "ACME.TEST", WorkspaceRef.Status.ACTIVE, "acme", null)));
        service = new UserCreationServiceImpl(scope, lookup, Optional.empty(), repository, registry, audit, users);
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private void error(String input, String field, String code, HttpStatus status, Map<String, Object> params)
            throws Exception {
        JsonNode body = json(input);
        assertThatThrownBy(() -> service.create(body)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getField()).isEqualTo(field);
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getParams()).isEqualTo(params);
        });
        verify(repository, never()).insertAccount(any(), anyString(), any());
        verifyNoInteractions(registry, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":null}", "{\"email\":42}", "{\"email\":\"  \"}"})
    void rejectsMissingEmailBeforeRoles_AC_005_10(String input) throws Exception {
        error(input, "email", "MSG01", HttpStatus.BAD_REQUEST, Map.of("field_name", "Email"));
    }

    @Test
    void measuresRawLengthBeforeFormat_AC_005_10() throws Exception {
        error(
                mapper.writeValueAsString(Map.of("email", " ".repeat(100) + "x")),
                "email",
                "MSG02",
                HttpStatus.BAD_REQUEST,
                Map.of("field_name", "Email", "max_length", 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "a@other.test", "a b@acme.test", "a@@acme.test"})
    void rejectsFormatAndDomainBeforeRoles_AC_005_10(String email) throws Exception {
        error(mapper.writeValueAsString(Map.of("email", email)), "email", "MSG03", HttpStatus.BAD_REQUEST, Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DEACTIVATED"})
    void duplicatePrecedesRoles_AC_005_11(String status) throws Exception {
        when(repository.accountStatus("a@acme.test")).thenReturn(Optional.of(status));
        error(
                "{\"email\":\" A@ACME.TEST \"}",
                "email",
                status.equals("ACTIVE") ? "MSG11" : "MSG111",
                HttpStatus.CONFLICT,
                Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"ADMIN\"", "[\"UNKNOWN\"]", "[1]"})
    void rejectsRolesBeforeDepartment_AC_005_12(String roles) throws Exception {
        error(
                "{\"email\":\"a@acme.test\",\"roles\":" + roles + ",\"departmentId\":\"bad\"}",
                "roles",
                "MSG01",
                HttpStatus.BAD_REQUEST,
                Map.of("field_name", "Roles"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"roles\":[\"PROJECT_MANAGER\"]}",
                "{\"roles\":[],\"departmentId\":\"bad\"}",
                "{\"roles\":[],\"departmentId\":42}",
                "{\"roles\":[],\"departmentId\":\"00000000-0000-4000-8000-000000000001\"}"
            })
    void rejectsDepartmentBeforeSpaces_AC_005_12(String fields) throws Exception {
        error(
                "{\"email\":\"a@acme.test\"," + fields.substring(1),
                "departmentId",
                "MSG01",
                HttpStatus.BAD_REQUEST,
                Map.of("field_name", "Department"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"roles\":[\"KNOWLEDGE_MANAGER\"]}",
                "{\"roles\":[],\"spaceIds\":[\"00000000-0000-4000-8000-000000000001\"]}",
                "{\"roles\":[\"KNOWLEDGE_MANAGER\"],\"spaceIds\":42}",
                "{\"roles\":[\"KNOWLEDGE_MANAGER\"],\"spaceIds\":[1]}",
                "{\"roles\":[\"KNOWLEDGE_MANAGER\"],\"spaceIds\":[\"bad\"]}"
            })
    void rejectsSpaces_AC_005_13(String fields) throws Exception {
        error(
                "{\"email\":\"a@acme.test\"," + fields.substring(1),
                "spaceIds",
                "MSG01",
                HttpStatus.BAD_REQUEST,
                Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsOccupiedSpace_AC_005_13() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.lockActiveSpaces(List.of(id)))
                .thenReturn(List.of(new UserCreationRepository.Space(1L, id, "Wiki")));
        when(repository.isOccupied(1L)).thenReturn(true);
        error(
                mapper.writeValueAsString(
                        Map.of("email", "a@acme.test", "roles", List.of("KNOWLEDGE_MANAGER"), "spaceIds", List.of(id))),
                "spaceIds",
                "MSG117",
                HttpStatus.CONFLICT,
                Map.of("space_name", "Wiki"));
    }

    @Test
    void registryRefusalDoesNotInsert_AC_005_16() throws Exception {
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        assertThatThrownBy(() -> service.create(body)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getCode()).isEqualTo("MSG155");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getField()).isEqualTo("email");
            assertThat(e.getParams()).isEmpty();
        });
        verify(repository, never()).insertAccount(any(), anyString(), any());
    }

    @Test
    void releasesClaimWhenInsertFails_AC_005_16() throws Exception {
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        when(repository.insertAccount(any(), anyString(), any())).thenThrow(new IllegalStateException("failed"));
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        assertThatThrownBy(() -> service.create(body)).isInstanceOf(IllegalStateException.class);
        verify(registry).release(workspace, "a@acme.test");
        verifyNoInteractions(audit);
    }

    @Test
    void usesFallbackOnlyWithoutLookupDomain_AC_005_15() {
        CompanyDomainFallback fallback = () -> "fallback.test";
        service = new UserCreationServiceImpl(scope, lookup, Optional.of(fallback), repository, registry, audit, users);
        assertThat(service.creationOptions().companyDomain()).isEqualTo("ACME.TEST");
        when(lookup.findById(workspace)).thenReturn(Optional.empty());
        assertThat(service.creationOptions().companyDomain()).isEqualTo("fallback.test");
        when(lookup.findById(workspace))
                .thenReturn(Optional.of(
                        new WorkspaceRef(workspace, "site", "  ", WorkspaceRef.Status.ACTIVE, "acme", null)));
        assertThat(service.creationOptions().companyDomain()).isEqualTo("fallback.test");
    }

    @Test
    void failsWithoutCompanyDomain_AC_005_15() throws Exception {
        when(lookup.findById(workspace)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creationOptions()).isInstanceOf(IllegalStateException.class);
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        assertThatThrownBy(() -> service.create(body)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsUnknownActiveSpace_AC_005_13() throws Exception {
        error(
                mapper.writeValueAsString(Map.of(
                        "email",
                        "a@acme.test",
                        "roles",
                        List.of("KNOWLEDGE_MANAGER"),
                        "spaceIds",
                        List.of(UUID.randomUUID()))),
                "spaceIds",
                "MSG01",
                HttpStatus.BAD_REQUEST,
                Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsMissingRoles_AC_005_12() throws Exception {
        error("{\"email\":\"a@acme.test\"}", "roles", "MSG01", HttpStatus.BAD_REQUEST, Map.of("field_name", "Roles"));
    }

    @Test
    void normalizesEmailRetainsEmployeeAndAuditsSuccessfulCreation_AC_005_9_AC_005_17() throws Exception {
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        when(repository.insertAccount(any(), anyString(), any())).thenReturn(true);
        when(users.findById(any()))
                .thenAnswer(call -> new UserAccountResponse(
                        call.getArgument(0),
                        null,
                        "a@acme.test",
                        List.of(Role.EMPLOYEE, Role.ADMIN),
                        null,
                        AccountStatus.ACTIVE,
                        null));
        JsonNode body = json("{\"email\":\" A@ACME.TEST \",\"roles\":[\"ADMIN\",\"ADMIN\"]}");
        UserAccountResponse response = service.create(body);
        assertThat(response.email()).isEqualTo("a@acme.test");
        assertThat(response.roles()).containsExactly(Role.EMPLOYEE, Role.ADMIN);
        assertThat(response.fullName()).isNull();
        assertThat(response.lastSignInAt()).isNull();
        verify(repository).insertRoles(response.id(), List.of(Role.EMPLOYEE, Role.ADMIN));
        var details = new java.util.HashMap<String, Object>();
        details.put("roles", List.of(Role.EMPLOYEE, Role.ADMIN));
        details.put("departmentId", null);
        details.put("spaceIds", List.of());
        verify(audit).record(AuditAction.ROLE_CHANGE, "USER", response.id(), details);
        verify(registry, never()).release(any(), anyString());
    }

    @Test
    void releasesClaimOnEmailConflictAtInsert_AC_005_11_AC_005_16() throws Exception {
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        assertThatThrownBy(() -> service.create(body)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getCode()).isEqualTo("MSG11");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        });
        verify(registry).release(workspace, "a@acme.test");
    }

    @Test
    void releasesClaimOnSpaceConflictAtInsert_AC_005_13_AC_005_16() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.lockActiveSpaces(List.of(id)))
                .thenReturn(List.of(new UserCreationRepository.Space(1L, id, "Wiki")));
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        when(repository.insertAccount(any(), anyString(), any())).thenReturn(true);
        JsonNode body = json(mapper.writeValueAsString(
                Map.of("email", "a@acme.test", "roles", List.of("KNOWLEDGE_MANAGER"), "spaceIds", List.of(id))));
        assertThatThrownBy(() -> service.create(body)).isInstanceOfSatisfying(FieldMessageCodeException.class, e -> {
            assertThat(e.getCode()).isEqualTo("MSG117");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getField()).isEqualTo("spaceIds");
            assertThat(e.getParams()).containsEntry("space_name", "Wiki");
        });
        verify(registry).release(workspace, "a@acme.test");
        verifyNoInteractions(audit);
    }

    @Test
    void releasesOnlyOnceOnRollbackAfterInsertFailure_AC_005_16() throws Exception {
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        when(repository.insertAccount(any(), anyString(), any())).thenThrow(new IllegalStateException());
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.create(body)).isInstanceOf(IllegalStateException.class);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(registry).release(workspace, "a@acme.test");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void releasesOnCommitFailureAndRetainsClaimAfterCommit_AC_005_16(boolean committed) throws Exception {
        when(registry.claim(workspace, "a@acme.test")).thenReturn(true);
        when(repository.insertAccount(any(), anyString(), any())).thenReturn(true);
        JsonNode body = json("{\"email\":\"a@acme.test\",\"roles\":[]}");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(body);
            var sync = TransactionSynchronizationManager.getSynchronizations().getFirst();
            sync.afterCompletion(
                    committed
                            ? TransactionSynchronization.STATUS_COMMITTED
                            : TransactionSynchronization.STATUS_ROLLED_BACK);
            if (committed) {
                verify(registry, never()).release(any(), anyString());
            } else {
                verify(registry).release(workspace, "a@acme.test");
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
