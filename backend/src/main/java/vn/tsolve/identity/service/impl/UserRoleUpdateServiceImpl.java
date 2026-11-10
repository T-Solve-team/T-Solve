package vn.tsolve.identity.service.impl;

import static vn.tsolve.identity.service.impl.HandoverChoices.choices;
import static vn.tsolve.identity.service.impl.HandoverChoices.matchesProjects;
import static vn.tsolve.identity.service.impl.HandoverChoices.matchesSpaces;
import static vn.tsolve.identity.service.impl.HandoverChoices.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse.ProjectOption;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse.SpaceOption;
import vn.tsolve.identity.repository.UserDetailRepository;
import vn.tsolve.identity.repository.UserRoleUpdateRepository;
import vn.tsolve.identity.repository.UserRoleUpdateRepository.Space;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.service.UserRoleUpdateService;
import vn.tsolve.identity.service.impl.HandoverChoices.Choice;
import vn.tsolve.identity.signin.port.TenantScope;

@Service
public class UserRoleUpdateServiceImpl implements UserRoleUpdateService {
    private static final Logger log = LoggerFactory.getLogger(UserRoleUpdateServiceImpl.class);
    private final TenantScope scope;
    private final UserDetailRepository read;
    private final UserRoleUpdateRepository write;
    private final UserDetailService detail;
    private final AuditService audit;

    public UserRoleUpdateServiceImpl(
            TenantScope scope,
            UserDetailRepository read,
            UserRoleUpdateRepository write,
            UserDetailService detail,
            AuditService audit) {
        this.scope = scope;
        this.read = read;
        this.write = write;
        this.detail = detail;
        this.audit = audit;
    }

    @Override
    @Transactional
    public UserDetailResponse update(UUID userId, JsonNode body, TSolveUser caller) {
        scope.currentWorkspaceId();
        write.serializeUpdates();
        if (!write.lockTarget(userId)) {
            throw new NotFoundException("MSG04", Map.of());
        }
        UserAccountResponse target = read.account(userId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (body == null || !body.isObject()) {
            throw malformed();
        }
        Set<Role> roles = roles(body.path("roles"));
        UUID departmentId = null;
        Long internalDepartment = null;
        if (!body.has("departmentId")) {
            throw required("departmentId", "Department");
        }
        if (!body.get("departmentId").isNull()) {
            departmentId = uuid(body.get("departmentId").asText(null));
            if (departmentId == null) {
                throw required("departmentId", "Department");
            }
            internalDepartment =
                    write.activeDepartment(departmentId).orElseThrow(() -> required("departmentId", "Department"));
        }
        if (roles.contains(Role.PROJECT_MANAGER) && departmentId == null) {
            throw required("departmentId", "Department");
        }
        List<Space> spaces = write.spaces();
        List<UUID> selected = selectedSpaces(body.path("spaceIds"), roles, spaces, userId);
        write.lockSpaces(selected);
        // Re-read ownership after waiting for a competing writer of a requested Space.
        spaces = write.spaces();
        selectedSpaces(body.path("spaceIds"), roles, spaces, userId);
        for (Space space : spaces) {
            if (selected.contains(space.id())
                    && space.managerId() != null
                    && !space.managerId().equals(userId)) {
                throw new BusinessRuleException("MSG117", Map.of("space_name", space.name()), "spaceIds");
            }
        }
        if (target.roles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            write.lockActiveAdmins();
            if (target.status() == AccountStatus.ACTIVE && write.activeAdminCount() == 1) {
                throw conflict("MSG56", Map.of());
            }
            if (userId.equals(caller.id())) {
                throw conflict("MSG113", Map.of());
            }
        }
        if (body.has("confirmTicketResolverRemoval")
                && !body.get("confirmTicketResolverRemoval").isBoolean()) {
            throw malformed();
        }
        List<Choice> projects = choices(body.get("projectHandovers"), "projectId");
        List<Choice> handoverSpaces = choices(body.get("spaceHandovers"), "spaceId");
        var recipientIds = new HashSet<UUID>();
        for (Choice choice : projects) {
            UUID recipient = uuid(choice.recipientId());
            if (recipient != null) recipientIds.add(recipient);
        }
        for (Choice choice : handoverSpaces) {
            UUID recipient = uuid(choice.recipientId());
            if (recipient != null) recipientIds.add(recipient);
        }
        write.lockRecipients(recipientIds);
        UserHandoverOptionsResponse options = read.handoverOptions(userId);
        UUID previousDepartment =
                target.department() == null ? null : target.department().departmentId();
        List<ProjectOption> affectedProjects =
                !roles.contains(Role.PROJECT_MANAGER) || !Objects.equals(departmentId, previousDepartment)
                        ? options.projects()
                        : List.of();
        List<SpaceOption> affectedSpaces = options.spaces().stream()
                .filter(item -> !selected.contains(item.space().spaceId()))
                .toList();
        for (ProjectOption option : affectedProjects) {
            if (option.recipients().isEmpty()) {
                throw conflict(
                        "MSG59",
                        Map.of("department_name", option.project().department().name()));
            }
        }
        if (affectedSpaces.stream().anyMatch(item -> item.recipients().isEmpty())) {
            throw conflict("MSG118", Map.of());
        }
        if (!matchesProjects(affectedProjects, projects) || !matchesSpaces(affectedSpaces, handoverSpaces)) {
            throw new FieldMessageCodeException(
                    HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "Hand Over Responsibilities"), null);
        }
        Set<UUID> removed = new LinkedHashSet<>();
        boolean retainMember = roles.contains(Role.TICKET_RESOLVER);
        if (!retainMember) {
            removed.addAll(read.ticketResolverProjectIds(userId));
            projects.forEach(choice -> removed.add(UUID.fromString(choice.id())));
        }
        if (target.roles().contains(Role.TICKET_RESOLVER)
                && !removed.isEmpty()
                && !body.path("confirmTicketResolverRemoval").asBoolean(false)) {
            throw conflict(
                    "MSG78",
                    Map.of(
                            "user_name",
                            target.fullName() == null ? target.email() : target.fullName(),
                            "count",
                            removed.size()));
        }
        List<UUID> beforeSpaces =
                options.spaces().stream().map(item -> item.space().spaceId()).toList();
        try {
            for (Choice choice : projects) {
                write.transferProject(
                        userId, UUID.fromString(choice.id()), UUID.fromString(choice.recipientId()), retainMember);
            }
            for (Choice choice : handoverSpaces) {
                write.transferSpace(UUID.fromString(choice.id()), UUID.fromString(choice.recipientId()));
            }
            if (!retainMember && target.roles().contains(Role.TICKET_RESOLVER)) write.removeMembers(userId);
            write.replaceAssignments(userId, roles, internalDepartment, selected);
            Map<String, Object> history = new LinkedHashMap<>();
            history.put("before", assignments(target.roles(), previousDepartment, beforeSpaces));
            history.put("after", assignments(List.copyOf(roles), departmentId, selected));
            history.put(
                    "projectHandovers",
                    projects.stream()
                            .map(choice -> Map.of("projectId", choice.id(), "recipientId", choice.recipientId()))
                            .toList());
            history.put(
                    "spaceHandovers",
                    handoverSpaces.stream()
                            .map(choice -> Map.of("spaceId", choice.id(), "recipientId", choice.recipientId()))
                            .toList());
            history.put("removedProjectIds", List.copyOf(removed));
            audit.record(AuditAction.ROLE_CHANGE, "USER", userId, history);
            return detail.getDetail(userId, caller);
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause().getMessage() != null
                    && failure.getMostSpecificCause().getMessage().contains("uq_membership_one_manager")) {
                log.error("Role update violated uq_membership_one_manager; rolling back");
                throw new FieldMessageCodeException(HttpStatus.INTERNAL_SERVER_ERROR, "MSG06", Map.of(), null);
            }
            throw failure;
        }
    }

    private static Map<String, Object> assignments(List<Role> roles, UUID departmentId, List<UUID> spaces) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roles);
        result.put("departmentId", departmentId);
        result.put("spaceIds", spaces);
        return result;
    }

    private static Set<Role> roles(JsonNode node) {
        if (!node.isArray()) throw required("roles", "Roles");
        Set<Role> roles = EnumSet.of(Role.EMPLOYEE);
        for (JsonNode value : node) {
            try {
                if (!value.isTextual()) throw new IllegalArgumentException();
                roles.add(Role.valueOf(value.textValue()));
            } catch (IllegalArgumentException invalid) {
                throw required("roles", "Roles");
            }
        }
        return roles;
    }

    private static List<UUID> selectedSpaces(JsonNode node, Set<Role> roles, List<Space> spaces, UUID userId) {
        if (!node.isArray()) throw required("spaceIds", "Spaces");
        List<UUID> selected = new ArrayList<>();
        for (JsonNode value : node) {
            UUID id = value.isTextual() ? uuid(value.textValue()) : null;
            Space space = spaces.stream()
                    .filter(item -> item.id().equals(id))
                    .findFirst()
                    .orElse(null);
            if (id == null
                    || space == null
                    || selected.contains(id)
                    || (!space.active() && !userId.equals(space.managerId()))) {
                throw required("spaceIds", "Spaces");
            }
            selected.add(id);
        }
        if (roles.contains(Role.KNOWLEDGE_MANAGER) == selected.isEmpty()) throw required("spaceIds", "Spaces");
        return selected;
    }

    private static FieldMessageCodeException malformed() {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of(), null);
    }

    private static FieldMessageCodeException required(String field, String label) {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", label), field);
    }

    private static BusinessRuleException conflict(String code, Map<String, ?> params) {
        return new BusinessRuleException(code, params);
    }
}
