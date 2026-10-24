package vn.tsolve.review.manual;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.review.dto.ReviewActor;
import vn.tsolve.review.service.ReviewAccessService;

/** Test-classpath session adapter for the real frontend; absent from the production jar. */
@RestController
@Profile("manual-review")
@Hidden
public class ManualReviewSessionController {
    private final ReviewAccessService access;

    public ManualReviewSessionController(ReviewAccessService access) {
        this.access = access;
    }

    @GetMapping("/api/v1/me")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> session(CsrfToken csrf) {
        csrf.getToken(); // Materialize the cookie before the frontend's first mutation.
        ReviewActor actor = access.currentActor();
        boolean manager = !actor.managedProjects().isEmpty();
        long projectId = actor.readableProjects().iterator().next();
        String projectName = projectId == 10L ? "Manual IT" : "Manual HR";
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
                "user",
                Map.of("id", projectId, "fullName", actor.name(), "email", actor.id() + "@manual.example"),
                "roles",
                manager ? List.of("EMPLOYEE", "PROJECT_MANAGER") : List.of("EMPLOYEE"),
                "managedSpaces",
                List.of(),
                "workspaceReady",
                true,
                "projects",
                List.of(Map.of(
                        "projectId",
                        projectId,
                        "jiraProjectKey",
                        projectName,
                        "departmentId",
                        projectId,
                        "departmentName",
                        projectName,
                        "roleInProject",
                        manager ? "PROJECT_MANAGER" : "MEMBER")),
                "departments",
                manager ? List.of(Map.of("departmentId", projectId, "name", projectName)) : List.of()));
        result.put("department", manager ? Map.of("departmentId", projectId, "name", projectName) : null);
        return result;
    }
}
