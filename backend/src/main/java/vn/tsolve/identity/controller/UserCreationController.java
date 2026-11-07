package vn.tsolve.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.identity.dto.CreateUserRequest;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserCreationOptionsResponse;
import vn.tsolve.identity.service.UserCreationService;

@RestController
@Tag(name = "users")
@RequestMapping("/api/v1/users")
public class UserCreationController {
    private final UserCreationService service;
    private final ObjectMapper mapper;

    public UserCreationController(UserCreationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUser", summary = "Create a Workspace account")
    @ApiResponse(responseCode = "400", description = "Invalid account fields")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "409", description = "Email or Space conflict")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<UserAccountResponse> create(@RequestBody(required = false) String request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(CreateUserRequest.parse(request, mapper).body()));
    }

    @GetMapping("/creation-options")
    @Operation(operationId = "getUserCreationOptions", summary = "Get company domain and Active Spaces")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<UserCreationOptionsResponse> creationOptions() {
        return ResponseEntity.ok(service.creationOptions());
    }
}
