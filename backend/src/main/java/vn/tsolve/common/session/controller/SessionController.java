package vn.tsolve.common.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.session.dto.CurrentUserResponse;
import vn.tsolve.common.session.service.SessionService;

@RestController
@Tag(name = "session")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me")
    @Operation(
            operationId = "getMe",
            summary = "The signed-in user with all their roles, Department(s), Projects and managed Spaces.")
    @PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
    public ResponseEntity<CurrentUserResponse> getMe() {
        return ResponseEntity.ok(service.getMe());
    }
}
