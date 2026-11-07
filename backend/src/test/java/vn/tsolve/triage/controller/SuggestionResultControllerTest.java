package vn.tsolve.triage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.SuggestionProject;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.exception.InvalidHistoryFilterException;
import vn.tsolve.triage.exception.ProjectNotManagedException;
import vn.tsolve.triage.service.SuggestionResultService;

@WebMvcTest(SuggestionResultController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class SuggestionResultControllerTest {

    private static final String LIST = "/api/v1/suggestion-results";
    private static final String PM = "pm@example.test";
    private static final String ACCOUNT_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SuggestionResultService results;

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void returnsThePagedHistoryOfTheSignedInManager_AC_003_1() throws Exception {
        SuggestionResultSummary row = new SuggestionResultSummary(
                "ITSUP-1",
                null,
                new SuggestionProject("p-1", "ITSUP"),
                Instant.parse("2026-11-01T10:00:00Z"),
                2,
                1,
                "Check the VPN.",
                null);
        when(results.history(eq(PM), any())).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mvc.perform(get(LIST)
                        .param("answer", "NONE")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].issueKey").value("ITSUP-1"))
                .andExpect(jsonPath("$.content[0].project.key").value("ITSUP"))
                .andExpect(jsonPath("$.content[0].jiraIssueUrl").isEmpty())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithTSolveUser(
            id = ACCOUNT_ID,
            roles = {Role.TICKET_RESOLVER, Role.PROJECT_MANAGER})
    void allowsTheHistoryListWithCombinedRoles_AC_003_1() throws Exception {
        when(results.history(eq(ACCOUNT_ID), any(HistoryQuery.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void refusesACallerWithoutASession_AC_003_3() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        verifyNoInteractions(results);
    }

    @Test
    @WithMockUser(username = "resolver@example.test", roles = "TICKET_RESOLVER")
    void refusesTheListToANonProjectManagerWithMsg08_AC_003_3() throws Exception {
        mvc.perform(get(LIST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(results);
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void answersAnUnmanagedProjectWithMsg08_AC_003_3() throws Exception {
        when(results.history(eq(PM), any(HistoryQuery.class))).thenThrow(new ProjectNotManagedException());

        mvc.perform(get(LIST).param("projectId", "other"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void answersABadFilterWithItsCode_AC_003_4() throws Exception {
        when(results.history(eq(PM), any(HistoryQuery.class))).thenThrow(new InvalidHistoryFilterException("MSG39"));
        mvc.perform(get(LIST).param("from", "2026-02-30").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG39"));

        mvc.perform(get(LIST).param("page", "first"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithTSolveUser(id = ACCOUNT_ID, roles = Role.TICKET_RESOLVER)
    void masksTheDetailForANonProjectManagerWithMsg04_AC_004_2() throws Exception {
        when(results.detail(ACCOUNT_ID, false, "ITSUP-1")).thenThrow(new NotFoundException("MSG04", Map.of()));

        mvc.perform(get(LIST + "/ITSUP-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }
}
