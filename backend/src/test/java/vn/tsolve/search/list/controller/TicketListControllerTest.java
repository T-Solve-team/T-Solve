package vn.tsolve.search.list.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.service.TicketListService;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.search.list.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketListControllerTest {
    private static final String PATH = "/api/v1/tickets";
    private static final UUID TICKET = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID DEPARTMENT = UUID.fromString("70000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketListService tickets;

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void returnsPagedPublicIdsAndNullDueDate_AC_006_3_AC_006_5_AC_006_6_AC_006_7() throws Exception {
        TicketSummaryResponse row = new TicketSummaryResponse(
                TICKET,
                "ITSUP-1",
                "VPN problem",
                new TicketProjectRef(PROJECT, "ITSUP"),
                new TicketDepartmentRef(DEPARTMENT, "IT"),
                "CSV",
                "PUBLISHED",
                null,
                Instant.parse("2026-11-01T10:00:00Z"));
        when(tickets.list(any(), any())).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(TICKET.toString()))
                .andExpect(jsonPath("$.content[0].project.projectId").value(PROJECT.toString()))
                .andExpect(jsonPath("$.content[0].project.name").value("ITSUP"))
                .andExpect(jsonPath("$.content[0].department.departmentId").value(DEPARTMENT.toString()))
                .andExpect(jsonPath("$.content[0].reviewDueDate").isEmpty())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void answers401WithoutSession_AC_006_8() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser
    void answers403ForEmployee_AC_006_8() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsLongKeywordWithFieldError_AC_006_9() throws Exception {
        mvc.perform(get(PATH).param("keyword", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("keyword"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsNonIntegerPagingBeforeCallingService_AC_006_9() throws Exception {
        mvc.perform(get(PATH).param("page", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(tickets);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void passesOnlyContractFiltersAndSignedInUser_AC_006_10() throws Exception {
        when(tickets.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get(PATH).param("workspaceId", "other-workspace").param("source", "CSV"))
                .andExpect(status().isOk());
        ArgumentCaptor<TSolveUser> caller = ArgumentCaptor.forClass(TSolveUser.class);
        ArgumentCaptor<TicketListQuery> query = ArgumentCaptor.forClass(TicketListQuery.class);
        verify(tickets).list(caller.capture(), query.capture());
        org.assertj.core.api.Assertions.assertThat(caller.getValue().getName())
                .isEqualTo("00000000-0000-0000-0000-000000000001");
        org.assertj.core.api.Assertions.assertThat(query.getValue())
                .isEqualTo(new TicketListQuery(null, null, "CSV", null, null, null, null));
    }
}
