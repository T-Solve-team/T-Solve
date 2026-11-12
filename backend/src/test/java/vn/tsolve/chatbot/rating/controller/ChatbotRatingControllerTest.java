package vn.tsolve.chatbot.rating.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import vn.tsolve.chatbot.rating.dto.ChatbotRatingRequest;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.chatbot.rating.exception.InvalidChatbotRatingException;
import vn.tsolve.chatbot.rating.service.ChatbotRatingService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.chatbot.rating.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ChatbotRatingControllerTest {
    private static final String ANSWER_ID = "70000000-0000-0000-0000-000000000001";
    private static final String PATH = "/api/v1/chatbot/answers/{answerId}/rating";
    private static final TSolveUser USER = new TSolveUser(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "user@example.test", "Test User", Set.of());

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ChatbotRatingService ratings;

    @ParameterizedTest
    @EnumSource(ChatbotRating.class)
    @WithTSolveUser
    void savesRating_AC_008_1(ChatbotRating rating) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put(PATH, ANSWER_ID)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"" + rating.name() + "\"}"))
                    .andExpect(status().isNoContent());
        }
        verify(ratings, times(2)).rate(USER, ANSWER_ID, new ChatbotRatingRequest(rating));
    }

    @Test
    @WithTSolveUser
    void removesRating_AC_008_2() throws Exception {
        mvc.perform(delete(PATH, ANSWER_ID).with(csrf())).andExpect(status().isNoContent());
        verify(ratings).remove(USER, ANSWER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {ANSWER_ID, "80000000-0000-0000-0000-000000000001", "not-a-uuid"})
    @WithTSolveUser
    void answers404ForUnknownOrOthersAnswer_AC_008_3(String answerId) throws Exception {
        doThrow(new ChatbotAnswerNotFoundException()).when(ratings).rate(any(), eq(answerId), any());
        doThrow(new ChatbotAnswerNotFoundException()).when(ratings).remove(any(), eq(answerId));

        mvc.perform(put(PATH, answerId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(delete(PATH, answerId).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verify(ratings).rate(USER, answerId, new ChatbotRatingRequest(ChatbotRating.HELPFUL));
        verify(ratings).remove(USER, answerId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"rating\":null}", "{\"rating\":\"MAYBE\"}", "not json"})
    @WithTSolveUser
    void rejectsBadBody_AC_008_4(String body) throws Exception {
        boolean unreadable = "{\"rating\":\"MAYBE\"}".equals(body) || "not json".equals(body);
        if (!unreadable) {
            doThrow(new InvalidChatbotRatingException())
                    .when(ratings)
                    .rate(any(), eq(ANSWER_ID), argThat(request -> request == null || request.rating() == null));
        }

        var request = put(PATH, ANSWER_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        if (unreadable) {
            verifyNoInteractions(ratings);
        } else {
            verify(ratings).rate(eq(USER), eq(ANSWER_ID), argThat(value -> value == null || value.rating() == null));
        }
    }

    @Test
    void answers401WithoutSession_AC_008_5() throws Exception {
        mvc.perform(put(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(delete(PATH, ANSWER_ID).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(ratings);
    }

    @Test
    @WithTSolveUser(roles = {})
    void letsUserWithNoAssignedRolesRate_AC_008_5() throws Exception {
        mvc.perform(put(PATH, ANSWER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete(PATH, ANSWER_ID).with(csrf())).andExpect(status().isNoContent());
        verify(ratings).rate(USER, ANSWER_ID, new ChatbotRatingRequest(ChatbotRating.HELPFUL));
        verify(ratings).remove(USER, ANSWER_ID);
    }
}
