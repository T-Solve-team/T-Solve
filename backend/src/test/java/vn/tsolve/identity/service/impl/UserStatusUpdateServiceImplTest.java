package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.SpaceRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.dto.UserProjectResponse;
import vn.tsolve.identity.repository.UserDetailRepository;
import vn.tsolve.identity.repository.UserRoleUpdateRepository;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.signin.port.TenantScope;

class UserStatusUpdateServiceImplTest {
    private final TenantScope scope = mock(TenantScope.class);
    private final UserDetailRepository read = mock(UserDetailRepository.class);
    private final UserRoleUpdateRepository write = mock(UserRoleUpdateRepository.class);
    private final UserDetailService detail = mock(UserDetailService.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserStatusUpdateServiceImpl service =
            new UserStatusUpdateServiceImpl(scope, read, write, detail, audit);
    private final UUID target = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final TSolveUser caller =
            new TSolveUser(UUID.randomUUID(), "admin@example.test", "Admin", Set.of(Role.ADMIN));
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        when(scope.currentWorkspaceId()).thenReturn(UUID.randomUUID());
        when(write.lockTarget(target)).thenReturn(true);
        account(AccountStatus.ACTIVE, Role.EMPLOYEE);
        when(read.handoverOptions(target)).thenReturn(new UserHandoverOptionsResponse(List.of(), List.of()));
    }

    private void account(AccountStatus status, Role... roles) {
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target, "Target", "target@example.test", List.of(roles), null, status, null)));
    }

    private ObjectNode body(String status) {
        return mapper.createObjectNode().put("status", status);
    }

    private void noWrites() {
        verify(write, never()).updateStatus(any(), any());
        verify(write, never()).transferProject(any(), any(), any(), anyBoolean());
        verify(write, never()).transferSpace(any(), any());
        verify(write, never()).replaceAssignments(any(), any(), any(), any());
        verify(write, never()).removeMembers(any());
        verifyNoInteractions(audit);
    }

    private void failure(JsonNode body, String code, Map<String, ?> params, TSolveUser actor) {
        assertThatThrownBy(() -> service.update(target, body, actor))
                .isInstanceOf(CodedException.class)
                .satisfies(error -> {
                    CodedException coded = (CodedException) error;
                    assertThat(coded.getCode()).isEqualTo(code);
                    assertThat(coded.getField()).isNull();
                    assertThat(coded.getParams()).isEqualTo(params);
                    if (coded instanceof MessageCodeException message) {
                        assertThat(message.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    } else {
                        assertThat(error)
                                .isInstanceOf(
                                        code.equals("MSG04") ? NotFoundException.class : BusinessRuleException.class);
                    }
                });
        noWrites();
        verifyNoInteractions(detail);
    }

    private void failure(JsonNode body, String code) {
        failure(body, code, Map.of(), caller);
    }

    private void options(boolean projectEligible, boolean spaceEligible) {
        UserAccountResponse recipient = new UserAccountResponse(
                recipientId,
                "Recipient",
                "recipient@example.test",
                List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.KNOWLEDGE_MANAGER),
                null,
                AccountStatus.ACTIVE,
                null);
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(new UserHandoverOptionsResponse.ProjectOption(
                                new UserProjectResponse(
                                        projectId,
                                        "SUP",
                                        new DepartmentRefResponse(departmentId, "Support"),
                                        true,
                                        target),
                                projectEligible ? List.of(recipient) : List.of())),
                        List.of(new UserHandoverOptionsResponse.SpaceOption(
                                new SpaceRefResponse(spaceId, "Support space"),
                                spaceEligible ? List.of(recipient) : List.of()))));
    }

    private ObjectNode handovers() {
        ObjectNode body = body("DEACTIVATED");
        body.putArray("projectHandovers")
                .addObject()
                .put("projectId", projectId.toString())
                .put("recipientId", recipientId.toString());
        body.putArray("spaceHandovers")
                .addObject()
                .put("spaceId", spaceId.toString())
                .put("recipientId", recipientId.toString());
        return body;
    }

    @Test
    void targetBeforeMalformedBody_AC_006_11_AC_006_16() {
        when(write.lockTarget(target)).thenReturn(false);
        failure(null, "MSG04");
        var order = inOrder(scope, write);
        order.verify(scope).currentWorkspaceId();
        order.verify(write).serializeUpdates();
        order.verify(write).lockTarget(target);
        verifyNoInteractions(read);
    }

    @Test
    void accountMissingAfterLock_AC_006_11() {
        when(read.account(target)).thenReturn(Optional.empty());
        failure(null, "MSG04");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "[]",
                "42",
                "\"text\"",
                "{}",
                "{\"status\":null}",
                "{\"status\":true}",
                "{\"status\":\"active\"}",
                "{\"status\":\"OTHER\"}",
                "{\"status\":\"DEACTIVATED\",\"projectHandovers\":null}",
                "{\"status\":\"DEACTIVATED\",\"spaceHandovers\":{}}",
                "{\"status\":\"DEACTIVATED\",\"projectHandovers\":[3]}",
                "{\"status\":\"DEACTIVATED\",\"projectHandovers\":[{\"projectId\":4,\"recipientId\":\"x\"}]}",
                "{\"status\":\"DEACTIVATED\",\"spaceHandovers\":[{\"spaceId\":\"x\"}]}",
                "{\"status\":\"ACTIVE\",\"spaceHandovers\":[{\"spaceId\":\"x\",\"recipientId\":\"y\"}]}",
                "{\"status\":\"ACTIVE\",\"projectHandovers\":[{\"projectId\":\"x\",\"recipientId\":\"y\"}]}"
            })
    void rejectsMalformedBeforeAdminChecks_AC_006_12_AC_006_16(String value) throws Exception {
        account(AccountStatus.ACTIVE, Role.ADMIN);
        when(write.activeAdminCount()).thenReturn(1L);
        failure(mapper.readTree(value), "MSG06");
        verify(write, never()).lockActiveAdmins();
    }

    @Test
    void missingBody_AC_006_12() {
        failure(null, "MSG06");
    }

    @Test
    void lastAdminBeforeSelfAndHandovers_AC_006_13_AC_006_16() {
        account(AccountStatus.ACTIVE, Role.ADMIN);
        when(write.activeAdminCount()).thenReturn(1L);
        failure(
                body("DEACTIVATED"),
                "MSG56",
                Map.of(),
                new TSolveUser(target, "self@example.test", "Self", Set.of(Role.ADMIN)));
        var order = inOrder(write);
        order.verify(write).lockActiveAdmins();
        order.verify(write).activeAdminCount();
        verify(read, never()).handoverOptions(any());
    }

    @Test
    void selfBeforeHandoverChecks_AC_006_13_AC_006_16() {
        account(AccountStatus.ACTIVE, Role.ADMIN);
        when(write.activeAdminCount()).thenReturn(2L);
        failure(
                body("DEACTIVATED"),
                "MSG112",
                Map.of(),
                new TSolveUser(target, "self@example.test", "Self", Set.of(Role.ADMIN)));
        verify(read, never()).handoverOptions(any());
    }

    @Test
    void inactiveAdminSkipsLastAdminGuard_AC_006_13_AC_006_16() {
        account(AccountStatus.DEACTIVATED, Role.ADMIN);
        service.update(target, body("DEACTIVATED"), caller);
        verify(write, never()).lockActiveAdmins();
        noWrites();
    }

    @Test
    void projectUnavailableBeforeSpaceAndMatching_AC_006_14_AC_006_16() {
        options(false, false);
        failure(body("DEACTIVATED"), "MSG59", Map.of("department_name", "Support"), caller);
    }

    @Test
    void spaceUnavailableBeforeMatching_AC_006_14_AC_006_16() {
        options(true, false);
        failure(body("DEACTIVATED"), "MSG118");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingProject",
                "missingSpace",
                "duplicateProject",
                "duplicateSpace",
                "extraProject",
                "extraSpace",
                "badProject",
                "badSpace",
                "badRecipient"
            })
    void exactEligibleChoicesRequired_AC_006_14(String invalid) {
        options(true, true);
        ObjectNode body = handovers();
        switch (invalid) {
            case "missingProject" -> body.remove("projectHandovers");
            case "missingSpace" -> body.remove("spaceHandovers");
            case "duplicateProject" -> body.withArray("projectHandovers")
                    .add(body.withArray("projectHandovers").get(0).deepCopy());
            case "duplicateSpace" -> body.withArray("spaceHandovers")
                    .add(body.withArray("spaceHandovers").get(0).deepCopy());
            case "extraProject" -> body.withArray("projectHandovers")
                    .addObject()
                    .put("projectId", UUID.randomUUID().toString())
                    .put("recipientId", recipientId.toString());
            case "extraSpace" -> body.withArray("spaceHandovers")
                    .addObject()
                    .put("spaceId", UUID.randomUUID().toString())
                    .put("recipientId", recipientId.toString());
            case "badProject" -> ((ObjectNode)
                            body.withArray("projectHandovers").get(0))
                    .put("projectId", "bad");
            case "badSpace" -> ((ObjectNode) body.withArray("spaceHandovers").get(0)).put("spaceId", "bad");
            case "badRecipient" -> ((ObjectNode)
                            body.withArray("spaceHandovers").get(0))
                    .put("recipientId", "bad");
            default -> throw new AssertionError(invalid);
        }
        failure(body, "MSG01", Map.of("field_name", "Hand Over Responsibilities"), caller);
    }

    @Test
    void unrelatedHandoversRejected_AC_006_14() {
        failure(handovers(), "MSG01", Map.of("field_name", "Hand Over Responsibilities"), caller);
    }

    @Test
    void transfersWithRetainedMemberAndAudits_AC_006_9_AC_006_15_AC_006_17() {
        account(AccountStatus.ACTIVE, Role.TICKET_RESOLVER);
        options(true, true);
        UserDetailResponse response = mock(UserDetailResponse.class);
        when(detail.getDetail(target, caller)).thenReturn(response);
        assertThat(service.update(target, handovers(), caller)).isSameAs(response);
        var order = inOrder(write, read, audit, detail);
        order.verify(write).lockRecipients(Set.of(recipientId));
        order.verify(read).handoverOptions(target);
        order.verify(write).transferProject(target, projectId, recipientId, true);
        order.verify(write).transferSpace(spaceId, recipientId);
        order.verify(write).updateStatus(target, AccountStatus.DEACTIVATED);
        ArgumentCaptor<Map<String, Object>> history = ArgumentCaptor.captor();
        order.verify(audit).record(eq(AuditAction.ROLE_CHANGE), eq("USER"), eq(target), history.capture());
        order.verify(detail).getDetail(target, caller);
        assertThat(history.getValue())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "statusBefore",
                        AccountStatus.ACTIVE,
                        "statusAfter",
                        AccountStatus.DEACTIVATED,
                        "projectHandovers",
                        List.of(Map.of("projectId", projectId.toString(), "recipientId", recipientId.toString())),
                        "spaceHandovers",
                        List.of(Map.of("spaceId", spaceId.toString(), "recipientId", recipientId.toString()))));
        verify(write, never()).replaceAssignments(any(), any(), any(), any());
        verify(write, never()).removeMembers(any());
    }

    @Test
    void transfersWithoutRetainingMember_AC_006_15() {
        options(true, true);
        service.update(target, handovers(), caller);
        verify(write).transferProject(target, projectId, recipientId, false);
    }

    @Test
    void activationSkipsAllDeactivationGuards_AC_006_10() {
        account(AccountStatus.DEACTIVATED, Role.ADMIN);
        service.update(target, body("ACTIVE"), new TSolveUser(target, "self@example.test", "Self", Set.of(Role.ADMIN)));
        verify(write).updateStatus(target, AccountStatus.ACTIVE);
        verify(read, never()).handoverOptions(any());
        verify(write, never()).lockActiveAdmins();
        verify(write, never()).lockRecipients(any());
        verify(write, never()).replaceAssignments(any(), any(), any(), any());
        verify(write, never()).removeMembers(any());
        verify(write, never()).transferProject(any(), any(), any(), anyBoolean());
        verify(write, never()).transferSpace(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DEACTIVATED"})
    void noOpWritesNothingAndDoesNotAudit_AC_006_16_AC_006_17(String status) {
        account(AccountStatus.valueOf(status), Role.EMPLOYEE);
        UserDetailResponse response = mock(UserDetailResponse.class);
        when(detail.getDetail(target, caller)).thenReturn(response);
        assertThat(service.update(target, body(status), caller)).isSameAs(response);
        noWrites();
    }

    @Test
    void sameStatusStillAppliesRequiredHandovers_AC_006_16_AC_006_17() {
        account(AccountStatus.DEACTIVATED, Role.EMPLOYEE);
        options(true, true);
        service.update(target, handovers(), caller);
        verify(write).transferSpace(spaceId, recipientId);
        verify(audit).record(eq(AuditAction.ROLE_CHANGE), eq("USER"), eq(target), any());
    }

    @Test
    void unboundFailsBeforeRepositoryAccess_AC_006_18() {
        when(scope.currentWorkspaceId()).thenThrow(new IllegalStateException("unbound"));
        assertThatThrownBy(() -> service.update(target, body("ACTIVE"), caller))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(read, write, audit, detail);
    }

    @Test
    void managerConstraintMapsToInternalError_AC_006_16() {
        options(true, true);
        doThrow(new DataIntegrityViolationException("uq_membership_one_manager"))
                .when(write)
                .transferProject(target, projectId, recipientId, false);
        assertThatThrownBy(() -> service.update(target, handovers(), caller))
                .isInstanceOf(MessageCodeException.class)
                .satisfies(error -> {
                    MessageCodeException coded = (MessageCodeException) error;
                    assertThat(coded.getCode()).isEqualTo("MSG06");
                    assertThat(coded.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        verifyNoInteractions(audit, detail);
    }

    @Test
    void unrelatedDatabaseFailurePropagates_AC_006_16() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        doThrow(failure).when(write).updateStatus(target, AccountStatus.DEACTIVATED);
        assertThatThrownBy(() -> service.update(target, body("DEACTIVATED"), caller))
                .isSameAs(failure);
        verifyNoInteractions(audit, detail);
    }
}
