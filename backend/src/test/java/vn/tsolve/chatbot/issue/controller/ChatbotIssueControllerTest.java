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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.IssueDepartment;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
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
