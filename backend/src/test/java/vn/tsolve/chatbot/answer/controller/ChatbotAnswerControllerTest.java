package vn.tsolve.chatbot.answer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.CitedSolution;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.chatbot.answer.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ChatbotAnswerControllerTest {
    private static final String PATH = "/api/v1/chatbot/answers";
    private static final UUID ANSWER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ChatbotAnswerService answers;

    @ParameterizedTest
    @ValueSource(
            strings = {"{\"history\":[]}", "{\"question\":null,\"history\":[]}", "{\"question\":\"  \",\"history\":[]}"
            })
    @WithTSolveUser
    void rejectsBlankQuestion_AC_005_8(String body) throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"));
        verifyNoInteractions(answers);
    }

    @Test
    @WithTSolveUser
    void rejectsLongQuestion_AC_005_8() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "x".repeat(1001) + "\",\"history\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"));
        verifyNoInteractions(answers);
    }

    @Test
    void answers401WithoutSession_AC_005_9() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"question\",\"history\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(answers);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @WithTSolveUser
    void letsAnySignedInUserAsk_AC_005_9(Role role) throws Exception {
        TSolveUser user = new TSolveUser(ANSWER_ID, "asker@example.test", "Asker", Set.of(role));
        when(answers.ask(any(), any()))
                .thenReturn(new ChatbotAnswerResponse(
                        ANSWER_ID,
                        Outcome.ANSWERED,
                        "Answer [1]",
                        List.of(
                                new TicketSolutionCitation(
                                        1, new CitedSolution("symptoms", "cause", "steps", null, "scope")),
                                new WikiDocumentCitation(2, ANSWER_ID, "Policy"))));
        mvc.perform(post(PATH)
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"question\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").value(ANSWER_ID.toString()))
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.text").value("Answer [1]"))
                .andExpect(jsonPath("$.citations[0].type").value("TICKET_SOLUTION"))
                .andExpect(jsonPath("$.citations[0].solution.workaround").isEmpty())
                .andExpect(jsonPath("$.citations[1].type").value("WIKI_DOCUMENT"));
        verify(answers).ask(eq(user), any());
    }

    @Test
    @WithTSolveUser
    void writesCitationTypeDiscriminator_AC_006_1() throws Exception {
        UUID document = UUID.fromString("80000000-0000-0000-0000-000000000001");
        when(answers.ask(any(), any()))
                .thenReturn(new ChatbotAnswerResponse(
                        ANSWER_ID,
                        Outcome.ANSWERED,
                        "Policy [1] and fix [3]",
                        List.of(
                                new WikiDocumentCitation(1, document, "Policy"),
                                new TicketSolutionCitation(
                                        3, new CitedSolution("symptoms", "cause", "steps", "workaround", "scope")))));
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"question\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations.length()").value(2))
                .andExpect(jsonPath("$.citations[0].type").value("WIKI_DOCUMENT"))
                .andExpect(jsonPath("$.citations[0].number").value(1))
                .andExpect(jsonPath("$.citations[0].wikiDocumentId").value(document.toString()))
                .andExpect(jsonPath("$.citations[0].title").value("Policy"))
                .andExpect(jsonPath("$.citations[1].type").value("TICKET_SOLUTION"))
                .andExpect(jsonPath("$.citations[1].number").value(3))
                .andExpect(jsonPath("$.citations[1].solution.workaround").value("workaround"))
                .andExpect(jsonPath("$.citations[1].ticketId").doesNotExist())
                .andExpect(jsonPath("$.citations[1].title").doesNotExist());
    }

    @Test
    @WithTSolveUser
    void ignoresWorkspaceParameter_AC_005_10() throws Exception {
        when(answers.ask(any(), any()))
                .thenReturn(new ChatbotAnswerResponse(ANSWER_ID, Outcome.NO_SOURCE, "", List.of()));
        mvc.perform(post(PATH)
                        .param("workspaceId", "other")
                        .header("X-Workspace-Id", "other")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"question\",\"history\":[],\"workspaceId\":\"other\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<ChatbotQuestionRequest> request = ArgumentCaptor.forClass(ChatbotQuestionRequest.class);
        verify(answers).ask(any(), request.capture());
        assertThat(request.getValue()).isEqualTo(new ChatbotQuestionRequest("question", List.of()));
    }

    @Test
    @WithTSolveUser
    void answers503AndStoresNothing_AC_005_7() throws Exception {
        when(answers.ask(any(), any())).thenThrow(new AiUnavailableException());
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"question\",\"history\":[]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }
}
