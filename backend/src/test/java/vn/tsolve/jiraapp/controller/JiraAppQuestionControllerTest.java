package vn.tsolve.jiraapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJob;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility.Status;
import vn.tsolve.jiraapp.exception.JiraAppQuestionJobNotFoundException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.exception.JiraSiteNotConnectedException;
import vn.tsolve.jiraapp.service.JiraAppQuestionService;

@WebMvcTest(JiraAppQuestionController.class)
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAppQuestionControllerTest {

    private static final String QUESTIONS = "/api/v1/jira-app/questions";
    private static final String ELIGIBILITY = "/api/v1/jira-app/questions/eligibility";
    private static final JiraAppPrincipal CALLER =
            new JiraAppPrincipal("cloud-1", "jira-1", "https://api.atlassian.com/ex/jira/cloud-1", "DP");
    private static final ChatbotAnswerResponse ANSWER = new ChatbotAnswerResponse(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            Outcome.ANSWERED,
            "Restart the VPN [1]",
            List.of());

    private static final UUID JOB_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraAppQuestionService questions;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @Test
    void acceptsWithoutWaitingForTheChatbot_AC_010_4_BUG_343() throws Exception {
        when(questions.start(eq(CALLER), any())).thenReturn(new JiraAppQuestionJob(JOB_ID));

        mvc.perform(ask("{\"question\":\"VPN fails\",\"history\":[{\"question\":\"q\",\"answer\":\"a\"}]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.answer").doesNotExist());
        verify(questions)
                .start(CALLER, new JiraAppQuestionRequest("VPN fails", List.of(new ChatbotTurnRequest("q", "a"))));
    }

    @Test
    void readsTheCompletedAnswer_AC_010_4() throws Exception {
        when(questions.result(CALLER, JOB_ID))
                .thenReturn(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.DONE, ANSWER));
        mvc.perform(get(QUESTIONS + "/" + JOB_ID).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.answer.answerId").value(ANSWER.answerId().toString()))
                .andExpect(jsonPath("$.answer.text").value(ANSWER.text()))
                .andExpect(jsonPath("$.answer.citations").isArray());
        verify(questions).result(CALLER, JOB_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED"})
    void readsStatusWithoutAnAnswer_AC_010_4(String state) throws Exception {
        when(questions.result(CALLER, JOB_ID))
                .thenReturn(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.valueOf(state), null));
        mvc.perform(get(QUESTIONS + "/" + JOB_ID).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(state))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void answers404Msg06ForAnUnknownOrOtherOwnersJob_AC_010_16() throws Exception {
        when(questions.result(CALLER, JOB_ID)).thenThrow(new JiraAppQuestionJobNotFoundException());
        mvc.perform(get(QUESTIONS + "/" + JOB_ID).with(authentication(fit())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void refusesAJobReadWithoutFit_AC_010_16() throws Exception {
        mvc.perform(get(QUESTIONS + "/" + JOB_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(questions);
    }

    @Test
    void answersNoSource_AC_010_7() throws Exception {
        when(questions.result(CALLER, JOB_ID))
                .thenReturn(new JiraAppQuestionJobResult(
                        JiraAppQuestionJobResult.Status.DONE,
                        new ChatbotAnswerResponse(ANSWER.answerId(), Outcome.NO_SOURCE, "", List.of())));
        mvc.perform(get(QUESTIONS + "/" + JOB_ID).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.outcome").value("NO_SOURCE"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"history\":[]}",
                "{\"question\":null,\"history\":[]}",
                "{\"question\":\" \\n \",\"history\":[]}"
            })
    void rejectsABlankQuestion_AC_010_5(String body) throws Exception {
        mvc.perform(ask(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"));
        verifyNoInteractions(questions);
    }

    @Test
    void rejectsAQuestionLongerThan1000Characters_AC_010_5() throws Exception {
        mvc.perform(ask("{\"question\":\"" + "a".repeat(1001) + "\",\"history\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"));
        verifyNoInteractions(questions);
    }

    @Test
    void acceptsAQuestionOfExactly1000Characters_AC_010_5() throws Exception {
        when(questions.start(eq(CALLER), any())).thenReturn(new JiraAppQuestionJob(JOB_ID));

        mvc.perform(ask("{\"question\":\"" + "a".repeat(1000) + "\",\"history\":[]}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsMoreThanTenHistoryTurns_AC_010_3() throws Exception {
        String turns = String.join(",", Collections.nCopies(11, "{\"question\":\"q\",\"answer\":\"a\"}"));

        mvc.perform(ask("{\"question\":\"VPN fails\",\"history\":[" + turns + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("history"));
        verifyNoInteractions(questions);
    }

    @Test
    void answers403Msg124ForASiteOfNoWorkspace_AC_010_11() throws Exception {
        when(questions.start(eq(CALLER), any())).thenThrow(new JiraSiteNotConnectedException());

        mvc.perform(ask("{\"question\":\"VPN fails\",\"history\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG124"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"));
    }

    @Test
    void answers403Msg08ForAProjectThatIsNotConnected_AC_010_11() throws Exception {
        when(questions.start(eq(CALLER), any())).thenThrow(new JiraIssueAccessDeniedException());

        mvc.perform(ask("{\"question\":\"VPN fails\",\"history\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void ratesTheAnswerAnd204_AC_010_15() throws Exception {
        UUID answerId = UUID.randomUUID();

        mvc.perform(put(QUESTIONS + "/" + answerId + "/rating")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}"))
                .andExpect(status().isNoContent());
        verify(questions).rate(CALLER, answerId, new JiraAppRatingRequest(ChatbotRating.HELPFUL));
    }

    @Test
    void rejectsARatingWithoutAValueAndAnswersMsg04ForAnotherProjectsAnswer_AC_010_15() throws Exception {
        UUID answerId = UUID.randomUUID();
        mvc.perform(put(QUESTIONS + "/" + answerId + "/rating")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(questions);

        doThrow(new ChatbotAnswerNotFoundException()).when(questions).rate(eq(CALLER), eq(answerId), any());
        mvc.perform(put(QUESTIONS + "/" + answerId + "/rating")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"NOT_HELPFUL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    void answers503Msg06WhenThePoolIsFull_AC_010_18() throws Exception {
        when(questions.start(eq(CALLER), any())).thenThrow(new AiUnavailableException());

        mvc.perform(ask("{\"question\":\"VPN fails\",\"history\":[]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void rejectsAQuestionWithoutFit() throws Exception {
        mvc.perform(post(QUESTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"VPN fails\",\"history\":[]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(questions);
    }

    @Test
    void reportsWhetherTheProjectIsConnected_AC_010_14() throws Exception {
        when(questions.eligibility(CALLER)).thenReturn(new JiraAskerEligibility(Status.PROJECT_NOT_CONNECTED));

        mvc.perform(get(ELIGIBILITY).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROJECT_NOT_CONNECTED"));
    }

    @Test
    void rejectsAnEligibilityCallWithoutFit() throws Exception {
        mvc.perform(get(ELIGIBILITY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(questions);
    }

    private static MockHttpServletRequestBuilder ask(String body) {
        return post(QUESTIONS)
                .with(authentication(fit()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static JiraAppAuthentication fit() {
        return new JiraAppAuthentication(CALLER, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }
}
