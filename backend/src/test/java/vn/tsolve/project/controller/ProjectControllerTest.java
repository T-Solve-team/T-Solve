package vn.tsolve.project.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.exception.ProjectAccessDeniedException;
import vn.tsolve.project.exception.ProjectTrackerUnavailableException;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.TrackerStatus;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ProjectController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectControllerTest {
    private static final String PATH = "/api/v1/projects/connection-test";
    private static final String VALID = "{\"jiraProjectKey\":\"OPS\"}";
    private static final UUID PROJECT = new UUID(127, 10);
    private static final List<TrackerStatus> STATUSES =
            List.of(new TrackerStatus("10000", "To Do", false), new TrackerStatus("10002", "Done", true));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private ProjectService service;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"jiraProjectKey\":null}",
                "{\"jiraProjectKey\":\"\"}",
                "{\"jiraProjectKey\":\"   \"}",
                "{\"jiraProjectKey\":\"                     \"}"
            })
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesBlankKey_AC_001_2(String body) throws Exception {
        assertKeyError(perform(body), "MSG01", Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAAAAAAAAAAAAAAAAAAAA", "😀😀😀😀😀😀😀😀😀😀😀"})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesMoreThanTwentyUtf16Units_AC_001_3(String key) throws Exception {
        assertKeyError(perform(keyBody(key)), "MSG02", Map.of("field_name", "jiraProjectKey", "max_length", 20));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAAAAAAAAAAAAAAAAAAA", "😀😀😀😀😀😀😀😀😀😀"})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void acceptsTwentyUtf16UnitsWithoutLengthError_AC_001_3(String key) throws Exception {
        when(service.testConnection(any())).thenReturn(newResponse());
        perform(keyBody(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(service).testConnection(new ProjectConnectionTestRequest(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"jiraProjectKey\":}", "["})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesUnreadableJson_AC_001_20(String body) throws Exception {
        assertNoFieldProblem(perform(body), 400, "MSG06");
        verifyNoInteractions(service);
    }

    @Test
    void declaresProjectManagerMethodAccess_AC_007_1() {
        Method method = Arrays.stream(ProjectController.class.getMethods())
                .filter(candidate -> candidate.getName().equals("testConnection")
                        && Arrays.equals(
                                candidate.getParameterTypes(), new Class<?>[] {ProjectConnectionTestRequest.class}))
                .findFirst()
                .orElse(null);
        assertThat(method)
                .as("public testConnection(ProjectConnectionTestRequest) handler")
                .isNotNull();
        PreAuthorize access = method.getAnnotation(PreAuthorize.class);
        assertThat(access).isNotNull();
        assertThat(access.value()).contains(Roles.PROJECT_MANAGER).isEqualTo("hasRole('PROJECT_MANAGER')");
    }

    @Test
    void refusesAnonymous_AC_007_2() throws Exception {
        assertNoFieldProblem(perform(VALID), 401, "MSG07");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser
    void refusesEmployeeWithValidBody_AC_007_3() throws Exception {
        assertNoFieldProblem(perform(VALID), 403, "MSG08");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void refusesAdminWithValidBody_AC_007_3() throws Exception {
        assertNoFieldProblem(perform(VALID), 403, "MSG08");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser
    void validatesEmployeeBodyBeforeMethodAccess_AC_001_2_AC_007_3() throws Exception {
        assertKeyError(perform("{}"), "MSG01", Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void validatesAdminBodyBeforeMethodAccess_AC_001_2_AC_007_3() throws Exception {
        assertKeyError(perform("{}"), "MSG01", Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersUnreachableProjectConflict_AC_001_15() throws Exception {
        when(service.testConnection(any()))
                .thenThrow(new BusinessRuleException("MSG48", Map.of("tracker_name", "Jira")));
        perform(VALID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG48"))
                .andExpect(jsonPath("$.params").value(new LinkedHashMap<>(Map.of("tracker_name", "Jira"))))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersLinkedProjectConflictEnvelope_AC_001_5() throws Exception {
        Map<String, Object> params = Map.of("tracker_name", "Jira", "department_name", "IT Operations");
        when(service.testConnection(any())).thenThrow(new BusinessRuleException("MSG49", params, "jiraProjectKey"));
        perform(VALID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG49"))
                .andExpect(jsonPath("$.params").value(new LinkedHashMap<>(params)))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraProjectKey"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG49"))
                .andExpect(jsonPath("$.fieldErrors[0].params").value(new LinkedHashMap<>(params)));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersTrackerUnavailable_AC_001_16() throws Exception {
        when(service.testConnection(any())).thenThrow(new ProjectTrackerUnavailableException());
        assertNoFieldProblem(perform(VALID), 503, "MSG06");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersIneligibleCaller_AC_007_4() throws Exception {
        when(service.testConnection(any())).thenThrow(new ProjectAccessDeniedException());
        assertNoFieldProblem(perform(VALID), 403, "MSG08");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersUnboundWorkspace_AC_007_5() throws Exception {
        when(service.testConnection(any())).thenThrow(new IllegalStateException("no Workspace is bound"));
        assertNoFieldProblem(perform(VALID), 500, "MSG06");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void returnsNewProjectExampleWithoutProjectId_AC_001_9_AC_001_10() throws Exception {
        when(service.testConnection(any())).thenReturn(newResponse());
        perform(VALID)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"jiraProjectKey":"OPS","statuses":[
                          {"id":"10000","name":"To Do","done":false},
                          {"id":"10002","name":"Done","done":true}],
                         "reconnect":false,"doneStatusIds":["10002"],"reviewRange":3}
                        """, true))
                .andExpect(jsonPath("$.projectId").doesNotHaveJsonPath());
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void returnsReconnectWithExistingId_AC_001_9_AC_001_11() throws Exception {
        var statuses =
                List.of(new TrackerStatus("10001", "In Review", false), new TrackerStatus("10002", "Done", true));
        when(service.testConnection(any()))
                .thenReturn(new ProjectConnectionTestResponse("OLD", statuses, true, PROJECT, List.of("10001"), 5));
        perform(keyBody("OLD"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                mapper.writeValueAsString(Map.of(
                                        "jiraProjectKey",
                                        "OLD",
                                        "statuses",
                                        statuses,
                                        "reconnect",
                                        true,
                                        "projectId",
                                        PROJECT.toString(),
                                        "doneStatusIds",
                                        List.of("10001"),
                                        "reviewRange",
                                        5)),
                                true))
                .andExpect(jsonPath("$.reconnect").value(true))
                .andExpect(jsonPath("$.projectId").value(PROJECT.toString()));
    }

    private ResultActions perform(String body) throws Exception {
        return mvc.perform(
                post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String keyBody(String key) throws Exception {
        return mapper.writeValueAsString(Map.of("jiraProjectKey", key));
    }

    private void assertKeyError(ResultActions result, String code, Map<String, Object> params) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.params").value(new LinkedHashMap<>(params)))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraProjectKey"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(code))
                .andExpect(jsonPath("$.fieldErrors[0].params").value(new LinkedHashMap<>(params)));
    }

    private void assertNoFieldProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    private ProjectConnectionTestResponse newResponse() {
        return new ProjectConnectionTestResponse("OPS", STATUSES, false, null, List.of("10002"), 3);
    }
}
