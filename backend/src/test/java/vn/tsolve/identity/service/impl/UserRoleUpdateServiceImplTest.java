package vn.tsolve.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class UserRoleUpdateServiceImplTest {
    private final TenantScope scope = mock(TenantScope.class);
    private final UserDetailRepository read = mock(UserDetailRepository.class);
    private final UserRoleUpdateRepository write = mock(UserRoleUpdateRepository.class);
    private final UserDetailService detail = mock(UserDetailService.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserRoleUpdateServiceImpl service = new UserRoleUpdateServiceImpl(scope, read, write, detail, audit);
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
        account(List.of(Role.EMPLOYEE));
        when(read.handoverOptions(target)).thenReturn(new UserHandoverOptionsResponse(List.of(), List.of()));
    }

    private void account(List<Role> roles) {
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target, "Target", "target@example.test", roles, null, AccountStatus.ACTIVE, null)));
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private void failure(String body, String code, String field, Map<String, Object> params) throws Exception {
        failure(body, code, field, params, caller);
    }

    private void failure(String body, String code, String field, Map<String, Object> params, TSolveUser actor)
            throws Exception {
        JsonNode node = json(body);
        assertThatThrownBy(() -> service.update(target, node, actor))
                .isInstanceOf(CodedException.class)
                .satisfies(error -> {
                    CodedException coded = (CodedException) error;
                    assertThat(coded.getCode()).isEqualTo(code);
                    assertThat(coded.getField()).isEqualTo(field);
                    assertThat(coded.getParams()).isEqualTo(params);
                    if (coded instanceof MessageCodeException message) {
                        assertThat(message.getStatus())
                                .isEqualTo(
                                        code.equals("MSG06") || code.equals("MSG01")
                                                ? HttpStatus.BAD_REQUEST
                                                : HttpStatus.CONFLICT);
                    } else if (!(coded instanceof BusinessRuleException)) {
                        assertThat(coded).isInstanceOf(NotFoundException.class);
                    }
                });
        verify(write, never()).replaceAssignments(any(), any(), any(), any());
        verify(write, never()).removeMembers(any());
        verify(write, never()).transferProject(any(), any(), any(), anyBoolean());
        verify(write, never()).transferSpace(any(), any());
        verifyNoInteractions(audit, detail);
    }

    private ObjectNode body(Role... roles) {
        ObjectNode body = mapper.createObjectNode();
        var array = body.putArray("roles");
        for (Role role : roles) array.add(role.name());
        body.putNull("departmentId");
        body.putArray("spaceIds");
        return body;
    }

    private UserAccountResponse recipient(Role role) {
        return new UserAccountResponse(
                recipientId,
                "Recipient",
                "recipient@example.test",
                List.of(Role.EMPLOYEE, role),
                new DepartmentRefResponse(departmentId, "Support"),
                AccountStatus.ACTIVE,
                null);
    }

    private UserHandoverOptionsResponse.ProjectOption projectOption(List<UserAccountResponse> recipients) {
        return new UserHandoverOptionsResponse.ProjectOption(
                new UserProjectResponse(
                        projectId, "SUP", new DepartmentRefResponse(departmentId, "Support"), true, target),
                recipients);
    }

    private UserHandoverOptionsResponse.SpaceOption spaceOption(List<UserAccountResponse> recipients) {
        return new UserHandoverOptionsResponse.SpaceOption(new SpaceRefResponse(spaceId, "Support space"), recipients);
    }

    private ObjectNode handoverBody(boolean keepResolver) {
        ObjectNode body = keepResolver ? body(Role.TICKET_RESOLVER) : body();
        body.putArray("projectHandovers")
                .addObject()
                .put("projectId", projectId.toString())
                .put("recipientId", recipientId.toString());
        return body;
    }

    @Test
    void checksUnknownTargetBeforeBody_AC_007_11() throws Exception {
        when(write.lockTarget(target)).thenReturn(false);
        failure("[]", "MSG04", null, Map.of());
    }

    @Test
    void rejectsNonObject_AC_007_12() throws Exception {
        failure("[]", "MSG06", null, Map.of());
    }

    @Test
    void rejectsRolesBeforeDepartment_AC_007_13() throws Exception {
        failure("{\"roles\":[\"INVALID\"]}", "MSG01", "roles", Map.of("field_name", "Roles"));
    }

    @Test
    void rejectsMissingDepartmentBeforeSpaces_AC_007_13() throws Exception {
        failure("{\"roles\":[]}", "MSG01", "departmentId", Map.of("field_name", "Department"));
    }

    @Test
    void rejectsPmWithoutDepartment_AC_007_13() throws Exception {
        failure(
                "{\"roles\":[\"PROJECT_MANAGER\"],\"departmentId\":null}",
                "MSG01",
                "departmentId",
                Map.of("field_name", "Department"));
    }

    @Test
    void rejectsMissingSpaces_AC_007_14() throws Exception {
        failure("{\"roles\":[],\"departmentId\":null}", "MSG01", "spaceIds", Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsKnowledgeManagerWithoutSpaces_AC_007_14() throws Exception {
        failure(
                "{\"roles\":[\"KNOWLEDGE_MANAGER\"],\"departmentId\":null,\"spaceIds\":[]}",
                "MSG01",
                "spaceIds",
                Map.of("field_name", "Spaces"));
    }

    @Test
    void lastAdminWinsOverSelfAndMalformedConfirmation_AC_007_15() throws Exception {
        account(List.of(Role.EMPLOYEE, Role.ADMIN));
        when(write.activeAdminCount()).thenReturn(1L);
        failure(
                "{\"roles\":[],\"departmentId\":null,\"spaceIds\":[],\"confirmTicketResolverRemoval\":1}",
                "MSG56",
                null,
                Map.of(),
                new TSolveUser(target, "target@example.test", "Target", Set.of(Role.ADMIN)));
        verify(write).lockActiveAdmins();
    }

    @Test
    void rejectsBadConfirmShape_AC_007_12() throws Exception {
        failure(
                "{\"roles\":[],\"departmentId\":null,\"spaceIds\":[],\"confirmTicketResolverRemoval\":null}",
                "MSG06",
                null,
                Map.of());
    }

    @Test
    void rejectsBadHandoverShape_AC_007_12() throws Exception {
        failure(
                "{\"roles\":[],\"departmentId\":null,\"spaceIds\":[],\"projectHandovers\":[{\"projectId\":3,\"recipientId\":\"x\"}]}",
                "MSG06",
                null,
                Map.of());
    }

    @Test
    void rejectsUnrelatedHandover_AC_007_17() throws Exception {
        failure(
                "{\"roles\":[],\"departmentId\":null,\"spaceIds\":[],\"projectHandovers\":[{\"projectId\":\"x\",\"recipientId\":\"y\"}]}",
                "MSG01",
                null,
                Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void requiresConfirmationForRemovedMembership_AC_007_18() throws Exception {
        account(List.of(Role.EMPLOYEE, Role.TICKET_RESOLVER));
        when(read.ticketResolverProjectIds(target)).thenReturn(List.of(UUID.randomUUID()));
        failure(
                "{\"roles\":[],\"departmentId\":null,\"spaceIds\":[]}",
                "MSG78",
                null,
                Map.of("user_name", "Target", "count", 1));
    }

    @Test
    void refusesUnboundWorkspaceBeforeAnyRead_AC_007_22() throws Exception {
        when(scope.currentWorkspaceId()).thenThrow(new IllegalStateException("unbound"));
        JsonNode body = json("{}");
        assertThatThrownBy(() -> service.update(target, body, caller)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(write);
    }

    @Test
    void rejectsMissingRoles_AC_007_13() throws Exception {
        failure("{}", "MSG01", "roles", Map.of("field_name", "Roles"));
    }

    @Test
    void rejectsNonArrayRoles_AC_007_13() throws Exception {
        failure("{\"roles\":\"ADMIN\"}", "MSG01", "roles", Map.of("field_name", "Roles"));
    }

    @Test
    void rejectsMalformedDepartment_AC_007_13() throws Exception {
        failure(
                "{\"roles\":[],\"departmentId\":\"not-a-uuid\",\"spaceIds\":[]}",
                "MSG01",
                "departmentId",
                Map.of("field_name", "Department"));
    }

    @Test
    void rejectsInactiveOrUnknownDepartment_AC_007_13() throws Exception {
        ObjectNode body = body();
        body.put("departmentId", departmentId.toString());
        when(write.activeDepartment(departmentId)).thenReturn(Optional.empty());
        failure(body.toString(), "MSG01", "departmentId", Map.of("field_name", "Department"));
    }

    @Test
    void rejectsUnknownSpace_AC_007_14() throws Exception {
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString());
        failure(body.toString(), "MSG01", "spaceIds", Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsDuplicateSpace_AC_007_14() throws Exception {
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", true, null)));
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString()).add(spaceId.toString());
        failure(body.toString(), "MSG01", "spaceIds", Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsSpaceWithoutKnowledgeManagerRole_AC_007_14() throws Exception {
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", true, null)));
        ObjectNode body = body();
        body.withArray("spaceIds").add(spaceId.toString());
        failure(body.toString(), "MSG01", "spaceIds", Map.of("field_name", "Spaces"));
    }

    @Test
    void rejectsNewHiddenSpace_AC_007_14() throws Exception {
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", false, null)));
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString());
        failure(body.toString(), "MSG01", "spaceIds", Map.of("field_name", "Spaces"));
    }

    @Test
    void retainsAlreadyManagedHiddenSpace_AC_007_14() {
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", false, target)));
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString());
        service.update(target, body, caller);
        verify(write).replaceAssignments(eq(target), any(), eq(null), eq(List.of(spaceId)));
    }

    @Test
    void rejectsOccupiedSpaceBeforeAdminAndConfirmation_AC_007_14() throws Exception {
        account(List.of(Role.EMPLOYEE, Role.ADMIN));
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", true, recipientId)));
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString());
        body.put("confirmTicketResolverRemoval", "wrong");
        failure(body.toString(), "MSG117", "spaceIds", Map.of("space_name", "Support space"));
        verify(write, never()).lockActiveAdmins();
        verify(write).lockSpaces(List.of(spaceId));
    }

    @Test
    void rejectsSelfAdminRemovalBeforeMalformedHandover_AC_007_15() throws Exception {
        account(List.of(Role.EMPLOYEE, Role.ADMIN));
        when(write.activeAdminCount()).thenReturn(2L);
        ObjectNode body = body();
        body.put("projectHandovers", false);
        failure(
                body.toString(),
                "MSG113",
                null,
                Map.of(),
                new TSolveUser(target, "target@example.test", "Target", Set.of(Role.ADMIN)));
    }

    @Test
    void keepingAdminSkipsRemovalGuards_AC_007_15() {
        account(List.of(Role.EMPLOYEE, Role.ADMIN));
        service.update(
                target, body(Role.ADMIN), new TSolveUser(target, "target@example.test", "Target", Set.of(Role.ADMIN)));
        verify(write, never()).lockActiveAdmins();
        verify(write, never()).activeAdminCount();
    }

    @Test
    void deactivatedAdminMayLoseRoleWhenOneActiveAdminRemains_AC_007_15() {
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target,
                        "Target",
                        "target@example.test",
                        List.of(Role.EMPLOYEE, Role.ADMIN),
                        null,
                        AccountStatus.DEACTIVATED,
                        null)));
        when(write.activeAdminCount()).thenReturn(1L);
        service.update(target, body(), caller);
        verify(write).replaceAssignments(eq(target), any(), eq(null), eq(List.of()));
    }

    @Test
    void rejectsMalformedSpaceHandoverShape_AC_007_12() throws Exception {
        ObjectNode body = body();
        body.putNull("spaceHandovers");
        failure(body.toString(), "MSG06", null, Map.of());
    }

    @Test
    void rejectsProjectWithoutRecipientBeforeSpaceAndChoiceErrors_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of())), List.of(spaceOption(List.of()))));
        failure(body().toString(), "MSG59", null, Map.of("department_name", "Support"));
    }

    @Test
    void rejectsSpaceWithoutRecipientBeforeChoiceError_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(List.of(), List.of(spaceOption(List.of()))));
        failure(body().toString(), "MSG118", null, Map.of());
    }

    @Test
    void rejectsMissingProjectChoice_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        failure(body().toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void rejectsDuplicateProjectChoices_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        ObjectNode body = handoverBody(false);
        body.withArray("projectHandovers")
                .add(body.withArray("projectHandovers").get(0).deepCopy());
        failure(body.toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void rejectsIneligibleProjectRecipient_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        ObjectNode body = handoverBody(false);
        ((ObjectNode) body.withArray("projectHandovers").get(0))
                .put("recipientId", UUID.randomUUID().toString());
        failure(body.toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void rejectsMissingSpaceChoice_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(), List.of(spaceOption(List.of(recipient(Role.KNOWLEDGE_MANAGER))))));
        failure(body().toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void rejectsDuplicateSpaceChoices_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(), List.of(spaceOption(List.of(recipient(Role.KNOWLEDGE_MANAGER))))));
        ObjectNode body = body();
        var choices = body.putArray("spaceHandovers");
        choices.addObject().put("spaceId", spaceId.toString()).put("recipientId", recipientId.toString());
        choices.add(choices.get(0).deepCopy());
        failure(body.toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void rejectsIneligibleSpaceRecipient_AC_007_17() throws Exception {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(), List.of(spaceOption(List.of(recipient(Role.KNOWLEDGE_MANAGER))))));
        ObjectNode body = body();
        body.putArray("spaceHandovers")
                .addObject()
                .put("spaceId", spaceId.toString())
                .put("recipientId", target.toString());
        failure(body.toString(), "MSG01", null, Map.of("field_name", "Hand Over Responsibilities"));
    }

    @Test
    void departmentChangeTriggersHandoverOfOriginalDepartmentProjects_AC_007_16() throws Exception {
        UUID newDepartment = UUID.randomUUID();
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target,
                        "Target",
                        "target@example.test",
                        List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER),
                        new DepartmentRefResponse(departmentId, "Support"),
                        AccountStatus.ACTIVE,
                        null)));
        when(write.activeDepartment(newDepartment)).thenReturn(Optional.of(13L));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(List.of(projectOption(List.of())), List.of()));
        ObjectNode body = body(Role.PROJECT_MANAGER);
        body.put("departmentId", newDepartment.toString());
        failure(body.toString(), "MSG59", null, Map.of("department_name", "Support"));
    }

    @Test
    void retainedProjectDoesNotRequireHandover_AC_007_16() {
        account(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER));
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target,
                        "Target",
                        "target@example.test",
                        List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER),
                        new DepartmentRefResponse(departmentId, "Support"),
                        AccountStatus.ACTIVE,
                        null)));
        when(write.activeDepartment(departmentId)).thenReturn(Optional.of(12L));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(List.of(projectOption(List.of())), List.of()));
        ObjectNode body = body(Role.PROJECT_MANAGER);
        body.put("departmentId", departmentId.toString());
        service.update(target, body, caller);
        verify(write, never()).transferProject(any(), any(), any(), anyBoolean());
    }

    @Test
    void retainedSpaceDoesNotRequireHandover_AC_007_16() {
        when(write.spaces())
                .thenReturn(List.of(new UserRoleUpdateRepository.Space(spaceId, "Support space", true, target)));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(List.of(), List.of(spaceOption(List.of()))));
        ObjectNode body = body(Role.KNOWLEDGE_MANAGER);
        body.withArray("spaceIds").add(spaceId.toString());
        service.update(target, body, caller);
        verify(write, never()).transferSpace(any(), any());
    }

    @Test
    void projectHandoverKeepsMemberWhenResolverRetained_AC_007_19() {
        account(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.TICKET_RESOLVER));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        service.update(target, handoverBody(true), caller);
        verify(write).transferProject(target, projectId, recipientId, true);
        verify(write, never()).removeMembers(any());
    }

    @Test
    void projectHandoverRemovesMembershipWithoutResolver_AC_007_19() {
        account(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        service.update(target, handoverBody(false), caller);
        verify(write).transferProject(target, projectId, recipientId, false);
        verify(write, never()).removeMembers(target);
    }

    @Test
    void countsHandedOverProjectsTogetherWithResolverMemberships_AC_007_18() throws Exception {
        account(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.TICKET_RESOLVER));
        when(read.ticketResolverProjectIds(target)).thenReturn(List.of(UUID.randomUUID()));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        failure(handoverBody(false).toString(), "MSG78", null, Map.of("user_name", "Target", "count", 2));
    }

    @Test
    void confirmationAllowsMembershipRemoval_AC_007_18() {
        account(List.of(Role.EMPLOYEE, Role.TICKET_RESOLVER));
        when(read.ticketResolverProjectIds(target)).thenReturn(List.of(UUID.randomUUID()));
        ObjectNode body = body();
        body.put("confirmTicketResolverRemoval", true);
        service.update(target, body, caller);
        verify(write).removeMembers(target);
    }

    @Test
    void usesEmailWhenFullNameAbsent_AC_007_18() throws Exception {
        when(read.account(target))
                .thenReturn(Optional.of(new UserAccountResponse(
                        target,
                        null,
                        "target@example.test",
                        List.of(Role.EMPLOYEE, Role.TICKET_RESOLVER),
                        null,
                        AccountStatus.ACTIVE,
                        null)));
        when(read.ticketResolverProjectIds(target)).thenReturn(List.of(UUID.randomUUID()));
        failure(body().toString(), "MSG78", null, Map.of("user_name", "target@example.test", "count", 1));
    }

    @Test
    void storesNonPmDepartmentAsSentAndDeduplicatesRoles_AC_007_10() throws Exception {
        when(write.activeDepartment(departmentId)).thenReturn(Optional.of(12L));
        ObjectNode body = body(Role.EMPLOYEE, Role.TICKET_RESOLVER, Role.TICKET_RESOLVER);
        body.put("departmentId", departmentId.toString());
        service.update(target, body, caller);
        ArgumentCaptor<Collection<Role>> roles = ArgumentCaptor.captor();
        verify(write).replaceAssignments(eq(target), roles.capture(), eq(12L), eq(List.of()));
        assertThat(roles.getValue()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.TICKET_RESOLVER);
    }

    @Test
    void rechecksRecipientsOnlyAfterLockingThem_AC_007_17() {
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))), List.of()));
        service.update(target, handoverBody(false), caller);
        var order = inOrder(write, read);
        order.verify(write).lockRecipients(any());
        order.verify(read).handoverOptions(target);
        order.verify(write).transferProject(target, projectId, recipientId, false);
    }

    @Test
    void recordsOneAuditWithBeforeAfterAndHandoverHistory_AC_007_21() {
        account(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.KNOWLEDGE_MANAGER, Role.TICKET_RESOLVER));
        UUID memberProjectId = UUID.randomUUID();
        when(read.ticketResolverProjectIds(target)).thenReturn(List.of(memberProjectId));
        when(read.handoverOptions(target))
                .thenReturn(new UserHandoverOptionsResponse(
                        List.of(projectOption(List.of(recipient(Role.PROJECT_MANAGER)))),
                        List.of(spaceOption(List.of(recipient(Role.KNOWLEDGE_MANAGER))))));
        ObjectNode body = handoverBody(false);
        body.put("confirmTicketResolverRemoval", true);
        body.putArray("spaceHandovers")
                .addObject()
                .put("spaceId", spaceId.toString())
                .put("recipientId", recipientId.toString());
        UserDetailResponse response = UserDetailResponse.from(
                new UserAccountResponse(
                        target,
                        "Target",
                        "target@example.test",
                        List.of(Role.EMPLOYEE),
                        null,
                        AccountStatus.ACTIVE,
                        null),
                false,
                List.of(),
                List.of(),
                List.of());
        when(detail.getDetail(target, caller)).thenReturn(response);
        assertThat(service.update(target, body, caller)).isSameAs(response);
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.captor();
        verify(audit).record(eq(AuditAction.ROLE_CHANGE), eq("USER"), eq(target), details.capture());
        Map<String, Object> event = details.getValue();
        assertThat(event).containsKeys("before", "after", "projectHandovers", "spaceHandovers", "removedProjectIds");
        assertThat(event.get("before")).isInstanceOf(Map.class);
        assertThat(event.get("after")).isInstanceOf(Map.class);
        Map<?, ?> before = (Map<?, ?>) event.get("before");
        Map<?, ?> after = (Map<?, ?>) event.get("after");
        assertThat(before.get("roles"))
                .isEqualTo(List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.KNOWLEDGE_MANAGER, Role.TICKET_RESOLVER));
        assertThat(before.get("departmentId")).isNull();
        assertThat(before.get("spaceIds")).isEqualTo(List.of(spaceId));
        assertThat(after.get("roles")).isEqualTo(List.of(Role.EMPLOYEE));
        assertThat(after.get("departmentId")).isNull();
        assertThat(after.get("spaceIds")).isEqualTo(List.of());
        assertThat((Collection<?>) event.get("removedProjectIds")).hasSize(2);
        assertThat((Collection<?>) event.get("removedProjectIds")).isEqualTo(List.of(memberProjectId, projectId));
        assertThat((Collection<?>) event.get("projectHandovers")).hasSize(1);
        assertThat((Collection<?>) event.get("spaceHandovers")).hasSize(1);
        verify(write).transferSpace(spaceId, recipientId);
    }
}
