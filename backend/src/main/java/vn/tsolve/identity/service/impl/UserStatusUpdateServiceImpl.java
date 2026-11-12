package vn.tsolve.identity.service.impl;

import static vn.tsolve.identity.service.impl.HandoverChoices.choices;
import static vn.tsolve.identity.service.impl.HandoverChoices.matchesProjects;
import static vn.tsolve.identity.service.impl.HandoverChoices.matchesSpaces;
import static vn.tsolve.identity.service.impl.HandoverChoices.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vn.tsolve.identity.repository.UserDetailRepository;
import vn.tsolve.identity.repository.UserRoleUpdateRepository;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.service.UserStatusUpdateService;
import vn.tsolve.identity.service.impl.HandoverChoices.Choice;
import vn.tsolve.identity.signin.port.TenantScope;

@Service
public class UserStatusUpdateServiceImpl implements UserStatusUpdateService {
    private static final Logger log = LoggerFactory.getLogger(UserStatusUpdateServiceImpl.class);
    private final TenantScope scope;
    private final UserDetailRepository read;
    private final UserRoleUpdateRepository write;
    private final UserDetailService detail;
    private final AuditService audit;

    public UserStatusUpdateServiceImpl(
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
        if (body == null
                || !body.isObject()
                || !body.path("status").isTextual()
                || !(body.path("status").textValue().equals("ACTIVE")
                        || body.path("status").textValue().equals("DEACTIVATED"))) {
            throw malformed();
        }
        AccountStatus status = AccountStatus.valueOf(body.get("status").textValue());
        List<Choice> projects = choices(body.get("projectHandovers"), "projectId");
        List<Choice> spaces = choices(body.get("spaceHandovers"), "spaceId");
        if (status == AccountStatus.ACTIVE && (!projects.isEmpty() || !spaces.isEmpty())) {
            throw malformed();
        }
        if (status == AccountStatus.DEACTIVATED) {
            if (target.status() == AccountStatus.ACTIVE && target.roles().contains(Role.ADMIN)) {
                write.lockActiveAdmins();
                if (write.activeAdminCount() == 1) {
                    throw new BusinessRuleException("MSG56", Map.of());
                }
            }
            if (userId.equals(caller.id())) {
                throw new BusinessRuleException("MSG112", Map.of());
            }
            var recipientIds = new HashSet<UUID>();
            for (Choice choice : projects) {
                UUID recipient = uuid(choice.recipientId());
                if (recipient != null) recipientIds.add(recipient);
            }
            for (Choice choice : spaces) {
                UUID recipient = uuid(choice.recipientId());
                if (recipient != null) recipientIds.add(recipient);
            }
            write.lockRecipients(recipientIds);
            UserHandoverOptionsResponse options = read.handoverOptions(userId);
            for (ProjectOption option : options.projects()) {
                if (option.recipients().isEmpty()) {
                    throw new BusinessRuleException(
                            "MSG59",
                            Map.of(
                                    "department_name",
                                    option.project().department().name()));
                }
            }
            if (options.spaces().stream().anyMatch(option -> option.recipients().isEmpty())) {
                throw new BusinessRuleException("MSG118", Map.of());
            }
            if (!matchesProjects(options.projects(), projects) || !matchesSpaces(options.spaces(), spaces)) {
                throw new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "Hand Over Responsibilities"), null);
            }
        }
        if (status == target.status() && projects.isEmpty() && spaces.isEmpty()) {
            return detail.getDetail(userId, caller);
        }
        try {
            for (Choice choice : projects) {
                write.transferProject(
                        userId,
                        UUID.fromString(choice.id()),
                        UUID.fromString(choice.recipientId()),
                        target.roles().contains(Role.TICKET_RESOLVER));
            }
            for (Choice choice : spaces) {
                write.transferSpace(UUID.fromString(choice.id()), UUID.fromString(choice.recipientId()));
            }
            write.updateStatus(userId, status);
            Map<String, Object> history = new LinkedHashMap<>();
            history.put("statusBefore", target.status());
            history.put("statusAfter", status);
            history.put(
                    "projectHandovers",
                    projects.stream()
                            .map(choice -> Map.of("projectId", choice.id(), "recipientId", choice.recipientId()))
                            .toList());
            history.put(
                    "spaceHandovers",
                    spaces.stream()
                            .map(choice -> Map.of("spaceId", choice.id(), "recipientId", choice.recipientId()))
                            .toList());
            audit.record(AuditAction.ROLE_CHANGE, "USER", userId, history);
            return detail.getDetail(userId, caller);
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause().getMessage() != null
                    && failure.getMostSpecificCause().getMessage().contains("uq_membership_one_manager")) {
                log.error("Status update violated uq_membership_one_manager; rolling back");
                throw new FieldMessageCodeException(HttpStatus.INTERNAL_SERVER_ERROR, "MSG06", Map.of(), null);
            }
            throw failure;
        }
    }

    private static FieldMessageCodeException malformed() {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of(), null);
    }
}
