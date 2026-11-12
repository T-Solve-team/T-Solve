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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.dto.ProjectResponse;
import vn.tsolve.project.dto.ProjectSaveResult;
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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"missing", "", "   ", "                     "})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesBlankCreateKey_AC_002_1(String key) throws Exception {
        var body = createBody();
        if ("missing".equals(key)) {
            body.remove("jiraProjectKey");
        } else {
            body.put("jiraProjectKey", key);
        }
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG01",
                "jiraProjectKey",
                Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "AAAAAAAAAAAAAAAAAAAAA",
                "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"
            })
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesLongCreateKey_AC_002_1(String key) throws Exception {
        var body = createBody();
        body.put("jiraProjectKey", key);
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG02",
                "jiraProjectKey",
                Map.of("field_name", "jiraProjectKey", "max_length", 20));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "empty"})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void requiresCreateDoneStatuses_AC_002_4(String variant) throws Exception {
        var body = createBody();
        switch (variant) {
            case "missing" -> body.remove("doneStatusIds");
            case "null" -> body.put("doneStatusIds", null);
            case "empty" -> body.put("doneStatusIds", List.of());
            default -> throw new AssertionError(variant);
        }
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG01",
                "doneStatusIds",
                Map.of("field_name", "doneStatusIds"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void requiresCreateReviewRange_AC_002_6(boolean missing) throws Exception {
        var body = createBody();
        if (missing) {
            body.remove("reviewRange");
        } else {
            body.put("reviewRange", null);
        }
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG01",
                "reviewRange",
                Map.of("field_name", "reviewRange"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "{",
                "[",
                "{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":\"10002\",\"reviewRange\":3}",
                "{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":{},\"reviewRange\":3}",
                "{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":5,\"reviewRange\":3}",
                "{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":[\"10002\"],\"reviewRange\":\"abc\"}",
                "{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":[\"10002\"],\"reviewRange\":{}}"
            })
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void refusesUnreadableCreateBody_AC_002_16(String body) throws Exception {
        assertNoFieldProblem(createRequest(body), 400, "MSG06");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "2147483648", "0", "-1", "\"3\""})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void passesCreateNumbersWithoutTruncation_AC_002_7_AC_002_16(String number) throws Exception {
        when(service.create(any())).thenReturn(new ProjectSaveResult(savedProject(), true));
        createRequest("{\"jiraProjectKey\":\"OPS\",\"doneStatusIds\":[\"10002\"],\"reviewRange\":" + number + "}")
                .andExpect(status().isCreated());
        verify(service)
                .create(new CreateProjectRequest("OPS", List.of("10002"), new BigDecimal(number.replace("\"", ""))));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateRangeFieldError_AC_002_7() throws Exception {
        when(service.create(any()))
                .thenThrow(new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG44", Map.of(), "reviewRange"));
        assertCreateField(createRequest(validCreate()), 400, "MSG44", "reviewRange", Map.of());
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateSelectionFieldError_AC_002_5() throws Exception {
        Map<String, Object> params = Map.of("field_name", "doneStatusIds");
        when(service.create(any()))
                .thenThrow(new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG01", params, "doneStatusIds"));
        assertCreateField(createRequest(validCreate()), 400, "MSG01", "doneStatusIds", params);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateTrackerConflict_AC_002_3() throws Exception {
        when(service.create(any())).thenThrow(new BusinessRuleException("MSG48", Map.of("tracker_name", "Jira")));
        createRequest(validCreate())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG48"))
                .andExpect(jsonPath("$.params").value(new LinkedHashMap<>(Map.of("tracker_name", "Jira"))))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateTrackerUnavailable_AC_002_3() throws Exception {
        when(service.create(any())).thenThrow(new ProjectTrackerUnavailableException());
        assertNoFieldProblem(createRequest(validCreate()), 503, "MSG06");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateLostInsertEnvelope_AC_002_11() throws Exception {
        Map<String, Object> params = Map.of("tracker_name", "Jira", "department_name", "IT Operations");
        when(service.create(any())).thenThrow(new BusinessRuleException("MSG49", params, "jiraProjectKey"));
        assertCreateField(createRequest(validCreate()), 409, "MSG49", "jiraProjectKey", params)
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersCreateWriteFailure_AC_002_12(boolean integrity) throws Exception {
        RuntimeException failure =
                integrity ? new DataIntegrityViolationException("membership") : new IllegalStateException("audit");
        when(service.create(any())).thenThrow(failure);
        assertNoFieldProblem(createRequest(validCreate()), 500, "MSG06");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void returnsOnlyCreatedProject_AC_002_13() throws Exception {
        when(service.create(any())).thenReturn(new ProjectSaveResult(savedProject(), true));
        assertSavedBody(createRequest(validCreate()).andExpect(status().isCreated()));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void answers200WhenTheServiceReportsAnExistingProject() throws Exception {
        when(service.create(any())).thenReturn(new ProjectSaveResult(savedProject(), false));
        assertSavedBody(createRequest(validCreate()).andExpect(status().isOk()));
    }

    @Test
    void declaresCreateProjectManagerAccess_AC_007_1() throws Exception {
        Method method = ProjectController.class.getMethod("create", CreateProjectRequest.class);
        PreAuthorize access = method.getAnnotation(PreAuthorize.class);
        assertThat(access).isNotNull();
        assertThat(access.value()).contains(Roles.PROJECT_MANAGER).isEqualTo("hasRole('PROJECT_MANAGER')");
    }

    @Test
    void refusesAnonymousCreate_AC_007_2() throws Exception {
        assertNoFieldProblem(createRequest(validCreate()), 401, "MSG07");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser
    void refusesEmployeeCreate_AC_007_3() throws Exception {
        assertNoFieldProblem(createRequest(validCreate()), 403, "MSG08");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void refusesAdminCreate_AC_007_3() throws Exception {
        assertNoFieldProblem(createRequest(validCreate()), 403, "MSG08");
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser
    void validatesEmployeeCreateBeforeMethodSecurity_AC_002_1() throws Exception {
        var body = createBody();
        body.remove("jiraProjectKey");
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG01",
                "jiraProjectKey",
                Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void validatesAdminCreateBeforeMethodSecurity_AC_002_1() throws Exception {
        var body = createBody();
        body.remove("jiraProjectKey");
        assertCreateField(
                createRequest(mapper.writeValueAsString(body)),
                400,
                "MSG01",
                "jiraProjectKey",
                Map.of("field_name", "jiraProjectKey"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersIneligibleCreateCaller_AC_007_4() throws Exception {
        when(service.create(any())).thenThrow(new ProjectAccessDeniedException());
        assertNoFieldProblem(createRequest(validCreate()), 403, "MSG08");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersUnboundCreateWorkspace_AC_007_5() throws Exception {
        when(service.create(any())).thenThrow(new IllegalStateException("no Workspace is bound"));
        assertNoFieldProblem(createRequest(validCreate()), 500, "MSG06");
    }

    private ResultActions createRequest(String body) throws Exception {
        return mvc.perform(post("/api/v1/projects")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private LinkedHashMap<String, Object> createBody() {
        return new LinkedHashMap<>(
                Map.of("jiraProjectKey", "OPS", "doneStatusIds", List.of("10002"), "reviewRange", 3));
    }

    private String validCreate() throws Exception {
        return mapper.writeValueAsString(createBody());
    }

    private ProjectResponse savedProject() {
        return new ProjectResponse(PROJECT, new UUID(127, 1), "OPS", List.of("10002"), 3, "ACTIVE");
    }

    private void assertSavedBody(ResultActions result) throws Exception {
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json(
                                mapper.writeValueAsString(Map.of(
                                        "projectId",
                                        PROJECT.toString(),
                                        "departmentId",
                                        new UUID(127, 1).toString(),
                                        "jiraProjectKey",
                                        "OPS",
                                        "doneStatusIds",
                                        List.of("10002"),
                                        "reviewRange",
                                        3,
                                        "status",
                                        "ACTIVE")),
                                true))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    private ResultActions assertCreateField(
            ResultActions result, int httpStatus, String code, String field, Map<String, Object> params)
            throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(code));
        if (params.isEmpty()) {
            result.andExpect(jsonPath("$.params").isMap())
                    .andExpect(jsonPath("$.params").isEmpty())
                    .andExpect(jsonPath("$.fieldErrors[0].params").isMap())
                    .andExpect(jsonPath("$.fieldErrors[0].params").isEmpty());
        } else {
            result.andExpect(jsonPath("$.params").value(new LinkedHashMap<>(params)))
                    .andExpect(jsonPath("$.fieldErrors[0].params").value(new LinkedHashMap<>(params)));
        }
        return result;
    }
}
