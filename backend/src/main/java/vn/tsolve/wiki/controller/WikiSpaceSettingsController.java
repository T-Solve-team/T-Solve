package vn.tsolve.wiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.service.WikiPermissionService;

@RestController
@RequestMapping("/api/v1/wiki")
@Tag(name = "wiki")
public class WikiSpaceSettingsController {
    private final WikiPermissionService service;

    public WikiSpaceSettingsController(WikiPermissionService service) {
        this.service = service;
    }

    @GetMapping("/spaces/{spaceId}/settings")
    @Operation(operationId = "getWikiSpaceSettings", summary = "Read a managed Space's header and View permissions")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: Space not found")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<WikiSpaceSettings> getSettings(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID spaceId) {
        return ResponseEntity.ok(service.getSettings(user.id(), spaceId));
    }

    @GetMapping("/spaces/{spaceId}/permission-candidates")
    @Operation(operationId = "listWikiPermissionCandidates", summary = "List eligible grantees of a managed Space")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: Space not found")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<WikiPermissionCandidates> listCandidates(
            @AuthenticationPrincipal TSolveUser user, @PathVariable UUID spaceId) {
        return ResponseEntity.ok(service.listCandidates(user.id(), spaceId));
    }

    @PostMapping("/spaces/{spaceId}/permissions")
    @Operation(operationId = "grantWikiPermissions", summary = "Grant View to Departments and users")
    @ApiResponse(responseCode = "400", description = "MSG01: empty grantees or missing lists")
    @ApiResponse(responseCode = "403", description = "MSG08: not the Space's Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: Space or grantee not found, inactive or own Department")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<Void> grant(
            @AuthenticationPrincipal TSolveUser user,
            @PathVariable UUID spaceId,
            @Valid @RequestBody GrantWikiPermissionsRequest request) {
        service.grant(user.id(), spaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/permissions/{permissionId}")
    @Operation(operationId = "revokeWikiPermission", summary = "Revoke a stored permission of a managed Space")
    @ApiResponse(responseCode = "403", description = "MSG08: not a Knowledge Manager")
    @ApiResponse(responseCode = "404", description = "MSG04: permission not found in a managed Space")
    @PreAuthorize("hasRole('" + Roles.KNOWLEDGE_MANAGER + "')")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal TSolveUser user, @PathVariable UUID permissionId) {
        service.revoke(user.id(), permissionId);
        return ResponseEntity.noContent().build();
    }
}
