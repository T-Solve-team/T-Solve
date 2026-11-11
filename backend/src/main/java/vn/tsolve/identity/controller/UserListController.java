package vn.tsolve.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.service.UserListService;

@RestController
@Tag(name = "users")
@RequestMapping("/api/v1/users")
public class UserListController {
    private final UserListService service;

    public UserListController(UserListService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "getUsers")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public PagedModel<UserAccountResponse> list(
            @AuthenticationPrincipal TSolveUser actor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam MultiValueMap<String, String> parameters) {
        return new PagedModel<>(service.list(UserListQuery.of(
                q, role, status, departmentId, page, size, parameters.getOrDefault("sort", List.of()))));
    }

    @GetMapping("/departments")
    @Operation(operationId = "getUserDepartments")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public List<DepartmentRefResponse> departments(@AuthenticationPrincipal TSolveUser actor) {
        return service.departments();
    }
}
