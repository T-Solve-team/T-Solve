package vn.tsolve.common.session;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.config.WebConfig;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.common.session.controller.SessionController;
import vn.tsolve.common.session.dto.CurrentUserResponse;
import vn.tsolve.common.session.dto.DepartmentRef;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.ProjectRole;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.dto.UserSummaryResponse;
import vn.tsolve.common.session.service.SessionService;

@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"server.port=0", "CORS_ALLOWED_ORIGINS=https://client.example.test"})
class SessionControllerTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID SPACE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SessionService sessionService;

    @Test
    @WithTSolveUser(roles = {Role.PROJECT_MANAGER, Role.KNOWLEDGE_MANAGER})
    void answers200WithEveryContractField_AC_003_1_AC_003_2() throws Exception {
        var department = new DepartmentRef(DEPARTMENT_ID, "Engineering");
        when(sessionService.getMe())
                .thenReturn(new CurrentUserResponse(
                        new UserSummaryResponse(USER_ID, "Test User", "user@example.test"),
                        List.of(Role.EMPLOYEE, Role.PROJECT_MANAGER, Role.KNOWLEDGE_MANAGER),
                        department,
                        List.of(department),
                        List.of(new ProjectMembership(
                                PROJECT_ID, "KAN", DEPARTMENT_ID, "Engineering", ProjectRole.PROJECT_MANAGER)),
                        List.of(new SpaceRef(SPACE_ID, "Engineering wiki")),
                        true));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.fullName").value("Test User"))
                .andExpect(jsonPath("$.user.email").value("user@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                .andExpect(jsonPath("$.roles[1]").value("PROJECT_MANAGER"))
                .andExpect(jsonPath("$.roles[2]").value("KNOWLEDGE_MANAGER"))
                .andExpect(jsonPath("$.department.departmentId").value(DEPARTMENT_ID.toString()))
                .andExpect(jsonPath("$.department.name").value("Engineering"))
                .andExpect(jsonPath("$.departments", hasSize(1)))
                .andExpect(jsonPath("$.departments[0].departmentId").value(DEPARTMENT_ID.toString()))
                .andExpect(jsonPath("$.projects[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.projects[0].jiraProjectKey").value("KAN"))
                .andExpect(jsonPath("$.projects[0].departmentId").value(DEPARTMENT_ID.toString()))
                .andExpect(jsonPath("$.projects[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$.projects[0].roleInProject").value("PROJECT_MANAGER"))
                .andExpect(jsonPath("$.managedSpaces[0].spaceId").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.managedSpaces[0].name").value("Engineering wiki"))
                .andExpect(jsonPath("$.workspaceReady").value(true));
    }

    @Test
    @WithTSolveUser
    void keepsDepartmentNullInTheBody_AC_003_4_edge14() throws Exception {
        when(sessionService.getMe())
                .thenReturn(new CurrentUserResponse(
                        new UserSummaryResponse(USER_ID, "Test User", "user@example.test"),
                        List.of(Role.EMPLOYEE),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        false));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("department")))
                .andExpect(jsonPath("$.department").value(nullValue()))
                .andExpect(jsonPath("$.departments", hasSize(0)))
                .andExpect(jsonPath("$.projects", hasSize(0)))
                .andExpect(jsonPath("$.managedSpaces", hasSize(0)))
                .andExpect(jsonPath("$.workspaceReady").value(false));
    }

    @Test
    void answers401Msg07WhenNobodyIsSignedIn_AC_003_5() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MSG07"));

        verifyNoInteractions(sessionService);
    }

    @Test
    @WithTSolveUser
    void answers500Msg06WhenTheServiceThrows_edge16() throws Exception {
        when(sessionService.getMe()).thenThrow(new RuntimeException("lookup is down"));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    // @WithTSolveUser always adds EMPLOYEE (TSolveUser constructor), so an Operator is stood in for by a plain
    // principal that holds only ROLE_OPERATOR (the OperatorPrincipal has no EMPLOYEE authority either).
    @Test
    @WithMockUser(roles = "OPERATOR")
    void edge28_answers403Msg08ForAPrincipalWithoutEmployee() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));

        verifyNoInteractions(sessionService);
    }
}
