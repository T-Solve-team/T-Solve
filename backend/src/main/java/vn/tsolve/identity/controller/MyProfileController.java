package vn.tsolve.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.session.dto.SpaceRef;
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
}
