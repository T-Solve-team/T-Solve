package vn.tsolve.search.query.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.service.TicketSearchService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.search.query.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketSearchControllerTest {
    private static final String PATH = "/api/v1/search/tickets";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketSearchService tickets;

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsMissingOrBlankQ_AC_007_7() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        for (String q : List.of("", "   ", "\t\n")) {
            mvc.perform(get(PATH).param("q", q))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        }
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsLongQ_AC_007_7() throws Exception {
        mvc.perform(get(PATH).param("q", "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsReversedDates_AC_007_7() throws Exception {
        mvc.perform(get(PATH)
                        .param("q", "VPN")
                        .param("resolvedFrom", "2026-10-02")
                        .param("resolvedTo", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG39"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsBadFilterOrPaging_AC_007_7() throws Exception {
        String[][] invalid = {
            {"departmentId", "not-a-uuid"},
            {"departmentId", "1-1-1-1-1"},
            {"departmentId", ""},
            {"source", "BAD"},
            {"source", "csv"},
            {"source", ""},
            {"page", "-1"},
            {"page", "2147483648"},
            {"page", "1.0"},
            {"page", ""},
            {"size", "0"},
            {"size", "101"},
            {"size", "ten"},
            {"size", ""}
        };
        for (String[] param : invalid) {
            mvc.perform(get(PATH).param("q", "VPN").param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG06"));
        }
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsDateNotInYyyyMmDd_AC_007_7() throws Exception {
        for (String field : List.of("resolvedFrom", "resolvedTo")) {
            for (String value : List.of(
                    "+999999999-12-31",
                    "2026-1-5",
                    "2026-02-30",
                    "2025-02-29",
                    "2026-10-01T00:00:00Z",
                    " 2026-10-01",
                    "")) {
                mvc.perform(get(PATH).param("q", "VPN").param(field, value))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("MSG06"));
            }
        }
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser
    void answers401And403_AC_007_8() throws Exception {
        mvc.perform(get(PATH).with(anonymous()).param("q", "VPN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(get(PATH).param("q", "VPN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void ignoresWorkspaceParameter_AC_006_10() throws Exception {
        when(tickets.search(any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get(PATH)
                        .param("q", "  VPN  ")
                        .param("workspaceId", "other")
                        .header("X-Workspace-Id", "other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20));
        ArgumentCaptor<TicketSearchQuery> query = ArgumentCaptor.forClass(TicketSearchQuery.class);
        verify(tickets).search(any(), query.capture());
        assertThat(query.getValue().q()).isEqualTo("VPN");
        assertThat(query.getValue().page()).isEqualTo(PageRequest.of(0, 20));
    }
}
