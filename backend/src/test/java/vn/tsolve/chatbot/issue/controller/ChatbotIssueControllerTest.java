package vn.tsolve.chatbot.issue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueCreateResult;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotJiraIssueResponse;
import vn.tsolve.chatbot.issue.dto.IssueDepartment;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.chatbot.issue.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ChatbotIssueControllerTest {
    private static final String ANSWER_ID = "70000000-0000-0000-0000-000000000001";
    private static final String PATH = "/api/v1/chatbot/answers/{answerId}/issue-draft";
    private static final String CREATE_PATH = "/api/v1/chatbot/answers/{answerId}/jira-issue";

    @Test
    @WithTSolveUser
    void rejectsMissingCreateFields_AC_011_5() throws Exception {
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.fieldErrors[?(@.field == 'summary')].code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'departmentId')].code")
                        .value("MSG01"));
        verifyNoInteractions(issues);
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    @WithTSolveUser
    void rejectsInvalidCreateFieldsBeforeService_AC_011_5(String body, String field, String code) throws Exception {
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')].code")
                        .value(code));
        verifyNoInteractions(issues);
    }

    private static Stream<Arguments> invalidCreateBodies() {
        return Stream.of(
                Arguments.of("{\"summary\":null,\"departmentId\":7}", "summary", "MSG01"),
                Arguments.of("{\"summary\":\"   \",\"departmentId\":7}", "summary", "MSG01"),
                Arguments.of("{\"summary\":\"\",\"departmentId\":7}", "summary", "MSG01"),
                Arguments.of("{\"summary\":\"" + "s".repeat(256) + "\",\"departmentId\":7}", "summary", "MSG02"),
                Arguments.of(
                        "{\"summary\":\"Help\",\"description\":\"" + "d".repeat(10001) + "\",\"departmentId\":7}",
                        "description",
                        "MSG02"),
                Arguments.of("{\"summary\":\"Help\",\"departmentId\":null}", "departmentId", "MSG01"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithTSolveUser(roles = {})
    void returnsCreatedOrExistingIssueForAnySignedInUser_AC_011_1_AC_011_3_AC_011_9(boolean created) throws Exception {
        when(issues.create(any(), eq(ANSWER_ID), any()))
                .thenReturn(new ChatbotIssueCreateResult(
                        new ChatbotJiraIssueResponse("HELP-12", "https://workspace.atlassian.net/browse/HELP-12"),
                        created));
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"description\":\"Details\",\"departmentId\":9000000000}"))
                .andExpect(status().is(created ? 201 : 200))
                .andExpect(jsonPath("$.issueKey").value("HELP-12"))
                .andExpect(jsonPath("$.issueUrl").value("https://workspace.atlassian.net/browse/HELP-12"))
                .andExpect(jsonPath("$.created").doesNotExist())
                .andExpect(jsonPath("$.issue").doesNotExist());
        verify(issues).create(any(), eq(ANSWER_ID), eq(new ChatbotIssueRequest("Help", "Details", 9000000000L)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"null", "\"\"", "\"  \""})
    @WithTSolveUser
    void acceptsEmptyNullOrMissingDescription_AC_011_5(String description) throws Exception {
        when(issues.create(any(), any(), any()))
                .thenReturn(new ChatbotIssueCreateResult(
                        new ChatbotJiraIssueResponse("HELP-12", "https://workspace.atlassian.net/browse/HELP-12"),
                        true));
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"departmentId\":7"
                                + (description == null ? "" : ",\"description\":" + description) + "}"))
                .andExpect(status().isCreated());
        verify(issues).create(any(), eq(ANSWER_ID), any());
    }

    @Test
    @WithTSolveUser
    void acceptsExactTextLengthLimits_AC_011_5() throws Exception {
        when(issues.create(any(), any(), any()))
                .thenReturn(new ChatbotIssueCreateResult(
                        new ChatbotJiraIssueResponse("HELP-12", "https://workspace.atlassian.net/browse/HELP-12"),
                        true));
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + "s".repeat(255) + "\",\"description\":\"" + "d".repeat(10000)
                                + "\",\"departmentId\":7}"))
                .andExpect(status().isCreated());
        verify(issues)
                .create(any(), eq(ANSWER_ID), eq(new ChatbotIssueRequest("s".repeat(255), "d".repeat(10000), 7L)));
    }

    @ParameterizedTest
    @CsvSource({"400,MSG06", "502,MSG148", "502,MSG157", "503,MSG149"})
    @WithTSolveUser
    void mapsCreateDepartmentAndJiraRefusals_AC_011_5_AC_011_6_AC_011_7_AC_011_8(int status, String code)
            throws Exception {
        when(issues.create(any(), any(), any()))
                .thenThrow(new MessageCodeException(HttpStatus.valueOf(status), code, Map.of()) {});
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"departmentId\":7}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @WithTSolveUser
    void mapsCreateWithoutJiraLink_AC_011_4() throws Exception {
        when(issues.create(any(), any(), any())).thenThrow(new BusinessRuleException("MSG146", Map.of()));
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"departmentId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG146"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ANSWER_ID, "not-a-uuid"})
    @WithTSolveUser
    void mapsUnknownOrOtherUsersCreateAnswerAndNonUuid_AC_011_9(String id) throws Exception {
        when(issues.create(any(), eq(id), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(post(CREATE_PATH, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"departmentId\":7}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verify(issues).create(any(), eq(id), any());
    }

    @Test
    void requiresWebSessionToCreate_AC_011_9() throws Exception {
        mvc.perform(post(CREATE_PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Help\",\"departmentId\":7}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(issues);
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ChatbotIssueService issues;

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "{}",
                "{\"history\":null}",
                "{\"history\":[null]}",
                "{\"history\":[{\"question\":null,\"answer\":\"\"}]}",
                "{\"history\":[{\"question\":\"q\"}]}",
                "not json"
            })
    @WithTSolveUser
    void rejectsMissingOrMalformedHistory_AC_010_8(String body) throws Exception {
        var request = post(PATH, ANSWER_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        verifyNoInteractions(issues);
    }

    @Test
    @WithTSolveUser(roles = {})
    void returnsDraftForUserWithoutRoles_AC_010_1() throws Exception {
        when(issues.draft(any(), eq(ANSWER_ID), any()))
                .thenReturn(new ChatbotIssueDraftResponse(
                        "Help", "Details", 7L, List.of(new IssueDepartment(7L, "Support"))));
        mvc.perform(post(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Help"))
                .andExpect(jsonPath("$.description").value("Details"))
                .andExpect(jsonPath("$.departmentId").value(7))
                .andExpect(jsonPath("$.departments[0].departmentId").value(7))
                .andExpect(jsonPath("$.departments[0].name").value("Support"))
                .andExpect(jsonPath("$.departments[0].jiraProjectKey").doesNotExist());
        verify(issues).draft(any(), eq(ANSWER_ID), eq(new ChatbotIssueDraftRequest(List.of())));
    }

    @Test
    @WithTSolveUser
    void returnsEmptyDraftWithExplicitNullDepartment_AC_010_6() throws Exception {
        when(issues.draft(any(), any(), any())).thenReturn(new ChatbotIssueDraftResponse("", "", null, List.of()));
        mvc.perform(post(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(""))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.departmentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.departments").isEmpty());
    }

    @Test
    @WithTSolveUser
    void mapsMissingJiraLink_AC_010_7() throws Exception {
        when(issues.draft(any(), any(), any())).thenThrow(new BusinessRuleException("MSG146", Map.of()));
        mvc.perform(post(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG146"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {ANSWER_ID, "not-a-uuid"})
    @WithTSolveUser
    void mapsUnknownAnswerIncludingNonUuid_AC_010_8(String id) throws Exception {
        when(issues.draft(any(), eq(id), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(post(PATH, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verify(issues).draft(any(), eq(id), any());
    }

    @Test
    void requiresWebSession_AC_010_8() throws Exception {
        mvc.perform(post(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"history\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(issues);
    }
}
