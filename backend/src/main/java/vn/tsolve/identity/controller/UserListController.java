package vn.tsolve.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.identity.dto.UserListDepartmentRef;
import vn.tsolve.identity.dto.UserListItem;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.service.UserListService;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users")
@ConditionalOnProperty(name = "tsolve.sql-workspace.enabled", havingValue = "true")
public class UserListController {
    private final UserListService users;

    public UserListController(UserListService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Operation(operationId = "getUsers", summary = "List users in the signed-in Admin's SQL Workspace")
    @ApiResponse(responseCode = "400", description = "Invalid filter, paging or sort")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or foreign Workspace session")
    @ApiResponse(responseCode = "403", description = "Current account is not an Admin")
    public ResponseEntity<PagedModel<UserListItem>> list(
            @RequestParam MultiValueMap<String, String> parameters, Authentication authentication) {
        return ResponseEntity.ok(new PagedModel<>(users.list(UserListQuery.parse(parameters), authentication)));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Operation(operationId = "getUserDepartments", summary = "List Department filters in the Admin's SQL Workspace")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or foreign Workspace session")
    @ApiResponse(responseCode = "403", description = "Current account is not an Admin")
    public ResponseEntity<List<UserListDepartmentRef>> departments(Authentication authentication) {
        return ResponseEntity.ok(users.departments(authentication));
    }
}
