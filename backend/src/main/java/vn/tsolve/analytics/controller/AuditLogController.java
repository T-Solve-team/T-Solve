package vn.tsolve.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.analytics.dto.AuditEntryResponse;
import vn.tsolve.analytics.service.AuditLogService;
import vn.tsolve.common.security.Roles;

@RestController
@Tag(name = "audit-log")
public class AuditLogController {
    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/audit-events")
    @Operation(operationId = "getAuditEvents")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ResponseEntity<PagedModel<AuditEntryResponse>> entries(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> parameters) {
        return ResponseEntity.ok(new PagedModel<>(
                service.findEntries(actor, action, from, to, page, size, parameters.getOrDefault("sort", List.of()))));
    }
}
