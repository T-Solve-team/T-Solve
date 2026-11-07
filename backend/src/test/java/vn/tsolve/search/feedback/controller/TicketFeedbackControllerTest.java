package vn.tsolve.search.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;
import vn.tsolve.search.feedback.service.TicketFeedbackService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.search.feedback.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketFeedbackControllerTest {
    private static final UUID TICKET = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final String PATH = "/api/v1/tickets/" + TICKET + "/feedback";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketFeedbackService feedback;

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"isHelpful\":null}"})
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsMissingIsHelpful_AC_008_4(String body) throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("isHelpful"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"));
        verifyNoInteractions(feedback);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsLongComment_AC_008_4() throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true,\"comment\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"));
        verifyNoInteractions(feedback);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsNonUuidTicketId_AC_008_4() throws Exception {
        mvc.perform(put("/api/v1/tickets/not-a-uuid/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(feedback);
    }

    @Test
    @WithTSolveUser
    void answers401And403_AC_008_5() throws Exception {
        mvc.perform(put(PATH)
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(feedback);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void ignoresWorkspaceParameter_AC_006_10() throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .param("workspaceId", "other-workspace")
                        .header("workspaceId", "other-workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":false,\"comment\":null}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        ArgumentCaptor<TSolveUser> caller = ArgumentCaptor.forClass(TSolveUser.class);
        verify(feedback).give(caller.capture(), eq(TICKET), eq(new TicketFeedbackRequest(false, null)));
        assertThat(caller.getValue().id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void returnsNoBody_AC_008_1() throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true,\"comment\":\"" + "x".repeat(500) + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(feedback).give(any(), eq(TICKET), eq(new TicketFeedbackRequest(true, "x".repeat(500))));
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void mapsInvisibleTicketToMsg04_AC_008_3() throws Exception {
        doThrow(new NotFoundException("MSG04", Map.of())).when(feedback).give(any(), eq(TICKET), any());
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isHelpful\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }
}
