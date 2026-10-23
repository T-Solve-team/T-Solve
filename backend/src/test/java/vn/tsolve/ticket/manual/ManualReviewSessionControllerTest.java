package vn.tsolve.ticket.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import vn.tsolve.ticket.dto.ReviewActor;
import vn.tsolve.ticket.service.ReviewAccessService;

class ManualReviewSessionControllerTest {
    @Test
    void suppliesFoundationMultiRoleSessionForManager() {
        var access = mock(ReviewAccessService.class);
        when(access.currentActor())
                .thenReturn(new ReviewActor("pm", "Local Manager", Map.of(10L, "Manual IT"), Set.of(10L)));
        var result = new ManualReviewSessionController(access)
                .session(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token"));
        assertThat(result)
                .containsEntry("roles", List.of("EMPLOYEE", "PROJECT_MANAGER"))
                .containsEntry("workspaceReady", true)
                .containsEntry("managedSpaces", List.of())
                .containsEntry("department", Map.of("departmentId", 10L, "name", "Manual IT"))
                .doesNotContainKey("role");
    }

    @Test
    void suppliesEmployeeSessionWithoutManagerDepartment() {
        var access = mock(ReviewAccessService.class);
        when(access.currentActor()).thenReturn(new ReviewActor("employee", "Local Employee", Map.of(), Set.of(10L)));
        var result = new ManualReviewSessionController(access)
                .session(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token"));
        assertThat(result)
                .containsEntry("roles", List.of("EMPLOYEE"))
                .containsEntry("department", null)
                .containsEntry("departments", List.of())
                .containsEntry("workspaceReady", true)
                .doesNotContainKey("role");
    }
}
