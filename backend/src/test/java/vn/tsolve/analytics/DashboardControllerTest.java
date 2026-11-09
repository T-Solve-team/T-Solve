package vn.tsolve.analytics;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.analytics.controller.DashboardController;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.exception.InvalidDashboardFilterException;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private DashboardService service;

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void servesAdminWithContractShape_AC_004_1() throws Exception {
        assertContract();
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void servesProjectManagerWithContractShape_AC_004_1() throws Exception {
        assertContract();
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void passesPrincipalAndRawFiltersToService_AC_004_1() throws Exception {
        TSolveUser user = (TSolveUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        mvc.perform(get("/api/v1/dashboard")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07")
                        .param("departmentId", "raw-department")
                        .param("projectId", "raw-project"))
                .andExpect(status().isOk());
        verify(service).dashboard(user, "2026-11-01", "2026-11-07", "raw-department", "raw-project");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MSG39", "MSG06"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersInvalidFilter_AC_004_3_AC_004_7(String code) throws Exception {
        when(service.dashboard(any(), any(), any(), any(), any())).thenThrow(new InvalidDashboardFilterException(code));
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersUnknownPublicId_AC_004_7() throws Exception {
        when(service.dashboard(any(), any(), any(), any(), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void rendersOutOfScopeDenial_AC_004_5() throws Exception {
        when(service.dashboard(any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("MSG08"));
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    @WithTSolveUser
    void rejectsEmployeeBeforeService_AC_004_14() throws Exception {
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousBeforeService_AC_004_14() throws Exception {
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    private void assertContract() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 7);
        var response = new KnowledgeDashboardResponse(
                new KnowledgeDashboardResponse.Period(date, date),
                new KnowledgeDashboardResponse.Options(List.of(), List.of()),
                new KnowledgeDashboardResponse.Outcomes(
                        0,
                        0,
                        0,
                        null,
                        0,
                        0,
                        List.of(new KnowledgeDashboardResponse.DayOutcome(date, 0, 0, 0)),
                        List.of()),
                new KnowledgeDashboardResponse.Coverage(0, 0, null, List.of()),
                new KnowledgeDashboardResponse.Feedback(0, 0, null, List.of(), List.of()),
                new KnowledgeDashboardResponse.Gaps(List.of(), 0, List.of()),
                new KnowledgeDashboardResponse.Suggestions(0, 0, null));
        when(service.dashboard(any(), any(), any(), any(), any())).thenReturn(response);
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-11-07"))
                .andExpect(jsonPath("$.period.to").value("2026-11-07"))
                .andExpect(jsonPath("$.options.departments").isArray())
                .andExpect(jsonPath("$.options.projects").isArray())
                .andExpect(jsonPath("$.outcomes.approved").value(0))
                .andExpect(jsonPath("$.outcomes.rejected").value(0))
                .andExpect(jsonPath("$.outcomes.changesRequested").value(0))
                .andExpect(jsonPath("$.outcomes.pendingReview").value(0))
                .andExpect(jsonPath("$.outcomes.expired").value(0))
                .andExpect(jsonPath("$.outcomes", hasKey("approvalRate")))
                .andExpect(jsonPath("$.outcomes.approvalRate").value(nullValue()))
                .andExpect(jsonPath("$.outcomes.perDay[0].date").value("2026-11-07"))
                .andExpect(jsonPath("$.outcomes.perDay[0].approved").value(0))
                .andExpect(jsonPath("$.outcomes.perDay[0].rejected").value(0))
                .andExpect(jsonPath("$.outcomes.perDay[0].changesRequested").value(0))
                .andExpect(jsonPath("$.outcomes.perProject").isArray())
                .andExpect(jsonPath("$.coverage.publishedTickets").value(0))
                .andExpect(jsonPath("$.coverage.allTickets").value(0))
                .andExpect(jsonPath("$.coverage", hasKey("publishedShare")))
                .andExpect(jsonPath("$.coverage.publishedShare").value(nullValue()))
                .andExpect(jsonPath("$.coverage.perDepartment").isArray())
                .andExpect(jsonPath("$.feedback.helpful").value(0))
                .andExpect(jsonPath("$.feedback.notHelpful").value(0))
                .andExpect(jsonPath("$.feedback", hasKey("helpfulRate")))
                .andExpect(jsonPath("$.feedback.helpfulRate").value(nullValue()))
                .andExpect(jsonPath("$.feedback.topRated").isArray())
                .andExpect(jsonPath("$.feedback.lowestRated").isArray())
                .andExpect(jsonPath("$.gaps.noResultSearches").isArray())
                .andExpect(jsonPath("$.gaps.chatbotNoSource").value(0))
                .andExpect(jsonPath("$.gaps.chatbotQuestions").isArray())
                .andExpect(jsonPath("$.suggestions.posted").value(0))
                .andExpect(jsonPath("$.suggestions.solved").value(0))
                .andExpect(jsonPath("$.suggestions", hasKey("solvedShare")))
                .andExpect(jsonPath("$.suggestions.solvedShare").value(nullValue()));
    }
}
