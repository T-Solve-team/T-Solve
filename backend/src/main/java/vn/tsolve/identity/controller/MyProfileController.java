package vn.tsolve.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.dto.TicketStatus;
import vn.tsolve.identity.dto.MySolutionsQuery;
import vn.tsolve.identity.service.MyProfileService;

@RestController
@Tag(name = "profile")
public class MyProfileController {
    private final MyProfileService service;

    public MyProfileController(MyProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/spaces")
    @Operation(operationId = "getMyViewableSpaces", summary = "The Spaces the signed-in user can view, by name.")
    @ApiResponse(responseCode = "401")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<List<SpaceRef>> getMyViewableSpaces() {
        return ResponseEntity.ok(service.getMyViewableSpaces());
    }

    @GetMapping("/api/v1/me/solutions")
    @Operation(
            operationId = "getMySolutions",
            summary = "The Tickets whose Solution the signed-in user wrote, newest update first.")
    @ApiResponse(responseCode = "400")
    @ApiResponse(responseCode = "401")
    @ApiResponse(responseCode = "403")
    @PreAuthorize("hasAnyRole('" + Roles.TICKET_RESOLVER + "','" + Roles.PROJECT_MANAGER + "')")
    public ResponseEntity<PagedModel<MySolution>> getMySolutions(
            @RequestParam(required = false)
                    @Parameter(
                            description = "Page number, starting at 0.",
                            schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
                    String page,
            @RequestParam(required = false)
                    @Parameter(
                            description = "Rows per page (SRS default 20).",
                            schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
                    String size,
            @RequestParam(required = false) @Parameter(schema = @Schema(implementation = TicketStatus.class))
                    String status,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "string", format = "uuid"))
                    String projectId,
            @RequestParam(required = false)
                    @Parameter(
                            description = "First day (inclusive, UTC+7).",
                            schema = @Schema(type = "string", format = "date"))
                    String from,
            @RequestParam(required = false)
                    @Parameter(
                            description = "Last day (inclusive, UTC+7); not before `from`.",
                            schema = @Schema(type = "string", format = "date"))
                    String to) {
        MySolutionsQuery query = MySolutionsQuery.parse(page, size, status, projectId, from, to);
        return ResponseEntity.ok(new PagedModel<>(service.getMySolutions(query)));
    }
}
