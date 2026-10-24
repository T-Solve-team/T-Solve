package vn.tsolve.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.review.config.ReviewSecurityConfig;
import vn.tsolve.review.dto.ReviewAction;
import vn.tsolve.review.dto.ReviewDecisionResult;
import vn.tsolve.review.exception.ReviewException;
import vn.tsolve.review.service.ReviewService;

@WebMvcTest(ReviewController.class)
@Import(ReviewSecurityConfig.class)
class ReviewControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReviewService review;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"approve", "reject", "request-changes"})
    @WithMockUser(roles = "PROJECT_MANAGER")
    void rejects501IdsAtHttpBoundary_AC_005_8_BUG_034(String action) throws Exception {
        String ids = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/v1/reviews/" + action)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[" + ids
                                + "],\"reason\":\"Valid reason\",\"comment\":\"Valid comment\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        org.mockito.Mockito.verifyNoInteractions(review);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"approve", "reject", "request-changes"})
    @WithMockUser(roles = "PROJECT_MANAGER")
    void accepts500IdsAtHttpBoundary_AC_005_8_BUG_034(String action) throws Exception {
        when(review.decide(any(), any(), any())).thenReturn(new ReviewDecisionResult(500));
        String ids = java.util.stream.LongStream.rangeClosed(1, 500)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/v1/reviews/" + action)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[" + ids
                                + "],\"reason\":\"Valid reason\",\"comment\":\"Valid comment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(500));
    }

    @Test
    void requiresAuthentication_AC_001_5() throws Exception {
        mvc.perform(get("/api/v1/review-queue")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void deniesNonManager_AC_001_5() throws Exception {
        mvc.perform(get("/api/v1/review-queue"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    @WithMockUser(roles = {"STAFF", "DEPARTMENT_MANAGER"})
    void refusesRetiredRoles_AC_001_5_BUG_031() throws Exception {
        mvc.perform(get("/api/v1/tickets/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void requiresReasonAndComment_AC_003_3_AC_004_3() throws Exception {
        mvc.perform(post("/api/v1/reviews/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1],\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"));
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1],\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void acceptsBulkChangeRequest_AC_004_1_AC_004_2() throws Exception {
        when(review.decide(List.of(1L, 2L), ReviewAction.REQUEST_CHANGES, "Add steps"))
                .thenReturn(new ReviewDecisionResult(2));
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1,2],\"comment\":\"Add steps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void usesSingleIdWithoutTrustingActorClaims_AC_006_1() throws Exception {
        when(review.decide(List.of(1L), ReviewAction.REQUEST_CHANGES, "Add steps"))
                .thenReturn(new ReviewDecisionResult(1));
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1],\"comment\":\"Add steps\",\"actorId\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void rendersStateConflictAsProblemDetail_AC_005_1() throws Exception {
        when(review.decide(any(), eq(ReviewAction.APPROVE), any()))
                .thenThrow(new ReviewException(HttpStatus.CONFLICT, "MSG06"));
        mvc.perform(post("/api/v1/reviews/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void keepsCsrfProtection_AC_005_1() throws Exception {
        mvc.perform(post("/api/v1/reviews/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void refusesFractionalTicketIds_AC_005_5_BUG_013() throws Exception {
        when(review.decide(any(), eq(ReviewAction.APPROVE), any())).thenReturn(new ReviewDecisionResult(1));
        mvc.perform(post("/api/v1/reviews/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1.5]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reviews/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[\"1\"],\"reason\":\"Incorrect\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[1.5],\"comment\":\"Add steps\"}"))
                .andExpect(status().isBadRequest());
    }
}
