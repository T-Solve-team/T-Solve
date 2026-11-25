package vn.tsolve.chatbot.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationSummaryResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationTurnResponse;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.chatbot.conversation.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ChatbotConversationControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private ChatbotConversationService conversations;

    @Test
    @WithTSolveUser(roles = {})
    void listsOwnConversationsPagedWithDefaultsWithoutAssignedRoles_AC_013_5_AC_013_10() throws Exception {
        var summary = new ChatbotConversationSummaryResponse(
                UUID.randomUUID(), "Title", Instant.parse("2026-11-18T00:00:00Z"));
        when(conversations.list(any(), any())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 41));
        mvc.perform(get("/api/v1/chatbot/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Title"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(41))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(conversations).list(any(), page.capture());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    @WithTSolveUser(roles = {})
    void readsSizeAbove100As100AndIgnoresClientSort_AC_013_5() throws Exception {
        when(conversations.list(any(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/chatbot/conversations?page=2&size=500&sort=title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(conversations).list(any(), page.capture());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(2, 100));
    }

    @Test
    @WithTSolveUser(roles = {})
    void opensStoredTurnsWithExplicitNullRating_AC_013_6_AC_013_10() throws Exception {
        UUID id = UUID.randomUUID();
        var turn = new ChatbotConversationTurnResponse(
                "Question",
                new ChatbotAnswerResponse(
                        UUID.randomUUID(), ChatbotAnswerResponse.Outcome.ANSWERED, "Answer", List.of()),
                null);
        when(conversations.get(any(), eq(id.toString())))
                .thenReturn(new ChatbotConversationResponse(
                        id, "Title", Instant.parse("2026-11-18T00:00:00Z"), List.of(turn)));
        mvc.perform(get("/api/v1/chatbot/conversations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns[0].answer.text").value("Answer"))
                .andExpect(jsonPath("$.turns[0].answer.chatSessionId").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rating\":null")));
    }

    @Test
    @WithTSolveUser(roles = {})
    void deletesOwnConversationWithCsrf_AC_013_7_AC_013_10() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(delete("/api/v1/chatbot/conversations/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
        verify(conversations).delete(any(), eq(id));
    }

    @Test
    @WithTSolveUser
    void mapsMalformedAndUnknownIdsTo404Msg04_AC_013_8() throws Exception {
        for (String id : List.of("not-a-uuid", UUID.randomUUID().toString())) {
            when(conversations.get(any(), eq(id))).thenThrow(new ChatbotConversationNotFoundException());
            doThrow(new ChatbotConversationNotFoundException())
                    .when(conversations)
                    .delete(any(), eq(id));
            mvc.perform(get("/api/v1/chatbot/conversations/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MSG04"));
            mvc.perform(delete("/api/v1/chatbot/conversations/{id}", id).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MSG04"));
        }
    }

    @Test
    void requiresWebSessionForAllEndpoints_AC_013_10() throws Exception {
        String path = "/api/v1/chatbot/conversations/" + UUID.randomUUID();
        mvc.perform(get("/api/v1/chatbot/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(delete(path).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(conversations);
    }

    @Test
    @WithTSolveUser
    void rejectsDeleteWithoutCsrf_AC_013_10() throws Exception {
        mvc.perform(delete("/api/v1/chatbot/conversations/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(conversations);
    }
}
