package vn.tsolve.identity.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserCreationOptionsResponse;
import vn.tsolve.identity.repository.UserCreationRepository;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.CompanyDomainFallback;
import vn.tsolve.identity.service.UserCreationService;
import vn.tsolve.identity.service.WorkspaceEmailRegistry;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

@Service
public class UserCreationServiceImpl implements UserCreationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final TenantScope scope;
    private final WorkspaceLookup lookup;
    private final Optional<CompanyDomainFallback> fallback;
    private final UserCreationRepository repository;
    private final WorkspaceEmailRegistry registry;
    private final AuditService audit;
    private final UserListRepository users;

    public UserCreationServiceImpl(
            TenantScope scope,
            WorkspaceLookup lookup,
            Optional<CompanyDomainFallback> fallback,
            UserCreationRepository repository,
            WorkspaceEmailRegistry registry,
            AuditService audit,
            UserListRepository users) {
        this.scope = scope;
        this.lookup = lookup;
        this.fallback = fallback;
        this.repository = repository;
        this.registry = registry;
        this.audit = audit;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserCreationOptionsResponse creationOptions() {
        UUID workspace = scope.currentWorkspaceId();
        return new UserCreationOptionsResponse(companyDomain(workspace), repository.activeSpaces());
    }

    private String companyDomain(UUID workspace) {
        return lookup.findById(workspace)
                .map(WorkspaceRef::googleDomain)
                .filter(domain -> !domain.isBlank())
                .or(() -> fallback.map(CompanyDomainFallback::companyDomain).filter(domain -> !domain.isBlank()))
                .orElseThrow(() -> new IllegalStateException("Workspace company domain is unavailable"));
    }

    @Override
    @Transactional
    public UserAccountResponse create(JsonNode input) {
        UUID workspace = scope.currentWorkspaceId();
        if (input == null || !input.isObject()) {
            throw error(null, "MSG03", HttpStatus.BAD_REQUEST, Map.of());
        }
        JsonNode rawEmail = input.path("email");
        if (!rawEmail.isTextual() || rawEmail.textValue().isBlank()) {
            throw required("email", "Email");
        }
        if (rawEmail.textValue().length() > 100) {
            throw error("email", "MSG02", HttpStatus.BAD_REQUEST, Map.of("field_name", "Email", "max_length", 100));
        }
        String email = rawEmail.textValue().strip().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()
                || !email.substring(email.indexOf('@') + 1).equalsIgnoreCase(companyDomain(workspace))) {
            throw error("email", "MSG03", HttpStatus.BAD_REQUEST, Map.of());
        }
        repository.accountStatus(email).ifPresent(status -> {
            throw error("email", "DEACTIVATED".equals(status) ? "MSG111" : "MSG11", HttpStatus.CONFLICT, Map.of());
        });
        JsonNode rawRoles = input.path("roles");
        if (!rawRoles.isArray()) {
            throw required("roles", "Roles");
        }
        EnumSet<Role> roles = EnumSet.of(Role.EMPLOYEE);
        for (JsonNode role : rawRoles) {
            try {
                if (!role.isTextual()) {
                    throw new IllegalArgumentException();
                }
                roles.add(Role.valueOf(role.textValue()));
            } catch (IllegalArgumentException invalid) {
                throw required("roles", "Roles");
            }
        }
        Long department = null;
        UUID departmentPublicId = null;
        JsonNode rawDepartment = input.path("departmentId");
        if (!rawDepartment.isMissingNode() && !rawDepartment.isNull()) {
            departmentPublicId = uuid(rawDepartment, "departmentId", "Department");
            department = repository
                    .activeDepartment(departmentPublicId)
                    .orElseThrow(() -> required("departmentId", "Department"));
        }
        if (roles.contains(Role.PROJECT_MANAGER) && department == null) {
            throw required("departmentId", "Department");
        }
        JsonNode rawSpaces = input.path("spaceIds");
        LinkedHashSet<UUID> spaceIds = new LinkedHashSet<>();
        if (!rawSpaces.isMissingNode() && !rawSpaces.isNull()) {
            if (!rawSpaces.isArray()) {
                throw required("spaceIds", "Spaces");
            }
            for (JsonNode id : rawSpaces) {
                spaceIds.add(uuid(id, "spaceIds", "Spaces"));
            }
        }
        if (roles.contains(Role.KNOWLEDGE_MANAGER) == spaceIds.isEmpty()) {
            throw required("spaceIds", "Spaces");
        }
        List<UserCreationRepository.Space> spaces = repository.lockActiveSpaces(List.copyOf(spaceIds));
        if (spaces.size() != spaceIds.size()) {
            throw required("spaceIds", "Spaces");
        }
        for (var space : spaces) {
            if (repository.isOccupied(space.id())) {
                throw occupied(space.name());
            }
        }
        if (!registry.claim(workspace, email)) {
            throw error("email", "MSG155", HttpStatus.CONFLICT, Map.of());
        }
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                registry.release(workspace, email);
            }
        };
        try {
            // Includes failures at commit and a later rollback by an enclosing transaction.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            release.run();
                        }
                    }
                });
            }
            UUID id = UUID.randomUUID();
            if (!repository.insertAccount(id, email, department)) {
                throw error("email", "MSG11", HttpStatus.CONFLICT, Map.of());
            }
            repository.insertRoles(id, List.copyOf(roles));
            for (var space : spaces) {
                if (!repository.insertManager(space.id(), id)) {
                    throw occupied(space.name());
                }
            }
            Map<String, Object> details = new HashMap<>();
            details.put("roles", List.copyOf(roles));
            details.put("departmentId", departmentPublicId);
            details.put("spaceIds", List.copyOf(spaceIds));
            audit.record(AuditAction.ROLE_CHANGE, "USER", id, details);
            return users.findById(id);
        } catch (RuntimeException | Error failure) {
            try {
                release.run();
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    private static UUID uuid(JsonNode value, String field, String label) {
        try {
            if (!value.isTextual()) {
                throw new IllegalArgumentException();
            }
            UUID id = UUID.fromString(value.textValue());
            if (!id.toString().equalsIgnoreCase(value.textValue())) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException invalid) {
            throw required(field, label);
        }
    }

    private static FieldMessageCodeException required(String field, String label) {
        return error(field, "MSG01", HttpStatus.BAD_REQUEST, Map.of("field_name", label));
    }

    private static FieldMessageCodeException occupied(String name) {
        return error("spaceIds", "MSG117", HttpStatus.CONFLICT, Map.of("space_name", name));
    }

    private static FieldMessageCodeException error(
            String field, String code, HttpStatus status, Map<String, Object> params) {
        return new FieldMessageCodeException(status, code, params, field);
    }
}
