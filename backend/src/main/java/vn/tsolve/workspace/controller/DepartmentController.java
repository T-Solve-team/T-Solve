package vn.tsolve.workspace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Roles;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.service.DepartmentService;

@RestController
@Tag(name = "departments")
@RequestMapping("/api/v1/departments")
public class DepartmentController {
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String DEPARTMENT_PATH = "/{departmentId:" + UUID_PATTERN + "}";
    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createDepartment", summary = "Create a Department and its Space")
    @ApiResponse(responseCode = "400", description = "Invalid Department name")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "409", description = "Department name already used")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<DepartmentRefResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping(DEPARTMENT_PATH)
    @Operation(operationId = "getDepartment", summary = "Read an Active Department")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public DepartmentRefResponse get(@PathVariable UUID departmentId) {
        return service.get(departmentId);
    }

    @PutMapping(value = DEPARTMENT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateDepartment", summary = "Rename a Department and its Space")
    @ApiResponse(responseCode = "400", description = "Invalid Department name")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @ApiResponse(responseCode = "409", description = "Department name already used")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public DepartmentRefResponse update(
            @PathVariable UUID departmentId, @Valid @RequestBody UpdateDepartmentRequest request) {
        return service.update(departmentId, request);
    }

    // Disjoint from DEPARTMENT_PATH: an id that is not a UUID is a missing Department, not a 400 (AC-003.2).
    @RequestMapping(
            value = "/{invalidDepartmentId:(?!" + UUID_PATTERN + "$).+}",
            method = {RequestMethod.GET, RequestMethod.PUT})
    @io.swagger.v3.oas.annotations.Hidden
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public DepartmentRefResponse invalidId() {
        throw new NotFoundException("MSG04", Map.of());
    }
}
