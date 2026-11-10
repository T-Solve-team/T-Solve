package vn.tsolve.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.service.UserRoleUpdateService;
import vn.tsolve.identity.service.UserStatusUpdateService;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users")
public class UserDetailController {
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String USER_PATH = "/{userId:" + UUID_PATTERN + "}";
    private final UserDetailService detailService;
    private final UserRoleUpdateService updateService;
    private final UserStatusUpdateService statusService;

    public UserDetailController(
            UserDetailService detailService,
            UserRoleUpdateService updateService,
            UserStatusUpdateService statusService) {
        this.detailService = detailService;
        this.updateService = updateService;
        this.statusService = statusService;
    }

    @GetMapping(USER_PATH)
    @Operation(operationId = "getUserDetail", summary = "Read an account and its current responsibilities")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public UserDetailResponse detail(@PathVariable UUID userId, @AuthenticationPrincipal TSolveUser caller) {
        return detailService.getDetail(userId, caller);
    }

    @GetMapping(USER_PATH + "/handover-options")
    @Operation(operationId = "getUserHandoverOptions", summary = "Read eligible handover recipients")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public UserHandoverOptionsResponse options(@PathVariable UUID userId, @AuthenticationPrincipal TSolveUser caller) {
        return detailService.getHandoverOptions(userId, caller);
    }

    @PutMapping(USER_PATH)
    @Operation(operationId = "updateUserRoles", summary = "Atomically update roles, Department and Spaces")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public UserDetailResponse update(
            @PathVariable UUID userId,
            @RequestBody(required = false) JsonNode body,
            @AuthenticationPrincipal TSolveUser caller) {
        return updateService.update(userId, body, caller);
    }

    @PutMapping(USER_PATH + "/status")
    @Operation(
            operationId = "updateUserStatus",
            summary = "Atomically change account status with the required handovers")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public UserDetailResponse updateStatus(
            @PathVariable UUID userId,
            @RequestBody(required = false) JsonNode body,
            @AuthenticationPrincipal TSolveUser caller) {
        return statusService.update(userId, body, caller);
    }

    // Disjoint from USER_PATH; static routes such as /departments retain precedence.
    @RequestMapping(
            value = {
                "/{invalidUserId:(?!" + UUID_PATTERN + "$).+}",
                "/{invalidUserId:(?!" + UUID_PATTERN + "$).+}/handover-options",
                "/{invalidUserId:(?!" + UUID_PATTERN + "$).+}/status"
            },
            method = {RequestMethod.GET, RequestMethod.PUT})
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public UserDetailResponse invalidId(@AuthenticationPrincipal TSolveUser caller) {
        throw new NotFoundException("MSG04", Map.of());
    }
}
