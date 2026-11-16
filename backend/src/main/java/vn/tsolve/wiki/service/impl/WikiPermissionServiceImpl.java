package vn.tsolve.wiki.service.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiPermissionRepository;
import vn.tsolve.wiki.service.WikiPermissionService;

@Service
@Transactional
public class WikiPermissionServiceImpl implements WikiPermissionService {
    private final WikiPermissionRepository permissions;
    private final AccessRepository access;
    private final AuditService audit;

    public WikiPermissionServiceImpl(
            WikiPermissionRepository permissions, AccessRepository access, AuditService audit) {
        this.permissions = permissions;
        this.access = access;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public WikiSpaceSettings getSettings(UUID userId, UUID spaceId) {
        return permissions.getSettings(requireManager(userId, spaceId));
    }

    @Override
    @Transactional(readOnly = true)
    public WikiPermissionCandidates listCandidates(UUID userId, UUID spaceId) {
        return permissions.listCandidates(requireManager(userId, spaceId));
    }

    @Override
    public void grant(UUID userId, UUID spaceId, GrantWikiPermissionsRequest request) {
        long internalId = requireManager(userId, spaceId);
        if (request.departmentIds().isEmpty() && request.userIds().isEmpty()) {
            throw new FieldMessageCodeException(
                    HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "grantees"), "grantees");
        }
        // Validate the whole batch, including existing grants, before any write. A stable order avoids deadlocks
        // when two batches overlap but list their grantees in different orders.
        Map<UUID, Long> departments = new LinkedHashMap<>();
        request.departmentIds().stream()
                .distinct()
                .sorted()
                .forEach(id -> departments.put(
                        id,
                        permissions
                                .findActiveDepartment(internalId, id)
                                .orElseThrow(WikiPermissionServiceImpl::notFound)));
        var users = request.userIds().stream().distinct().sorted().toList();
        for (UUID id : users) {
            if (!permissions.isActiveUser(id)) throw notFound();
        }
        Instant now = Instant.now();
        departments.forEach((id, internalDepartment) -> {
            if (permissions.insertDepartment(internalId, internalDepartment, userId, now) == 1) {
                record(spaceId, "GRANT", "DEPARTMENT", id);
            }
        });
        for (UUID id : users) {
            if (permissions.insertUser(internalId, id, userId, now) == 1) record(spaceId, "GRANT", "USER", id);
        }
    }

    @Override
    public void revoke(UUID userId, UUID permissionId) {
        var permission = permissions.findPermission(permissionId).orElseThrow(WikiPermissionServiceImpl::notFound);
        long internalId =
                permissions.findActiveSpace(permission.spaceId()).orElseThrow(WikiPermissionServiceImpl::notFound);
        // This endpoint has no Space path parameter: conceal permissions outside the caller's managed Spaces
        // (AC-006.7).
        if (!access.managesSpace(userId, internalId)) throw notFound();
        if (permissions.deletePermission(permissionId) != 1) throw notFound();
        record(permission.spaceId(), "REVOKE", permission.granteeType(), permission.granteeId());
    }

    private long requireManager(UUID userId, UUID spaceId) {
        long internalId = permissions.findActiveSpace(spaceId).orElseThrow(WikiPermissionServiceImpl::notFound);
        if (!access.managesSpace(userId, internalId)) throw new AccessDeniedException("MSG08");
        return internalId;
    }

    private void record(UUID spaceId, String operation, String type, UUID granteeId) {
        audit.record(
                AuditAction.PERMISSION_CHANGE,
                "WIKI_SPACE",
                spaceId,
                Map.of("operation", operation, "granteeType", type, "granteeId", granteeId));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("MSG04", Map.of());
    }
}
