package vn.tsolve.search.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.scope.dto.TicketDepartmentRef;
import vn.tsolve.search.scope.dto.TicketProjectRef;
import vn.tsolve.search.suggestion.dto.TicketSuggestionRequest;
import vn.tsolve.search.suggestion.service.TicketSuggestionService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "vn.tsolve.search.suggestion.controller.*"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketSuggestionControllerTest {
    private static final String PATH = "/api/v1/search/suggestions";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketSuggestionService service;

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsMissingOrLongSummary_AC_009_4() throws Exception {
        for (String body : List.of("{}", "{\"summary\":null}", "{\"summary\":\"\"}", "{\"summary\":\"   \"}")) {
            mvc.perform(post(PATH)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("summary"))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"));
        }
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("summary"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(255));
        verifyNoInteractions(service);

        when(service.suggest(any(), any())).thenReturn(List.of());
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + "x".repeat(255) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsLongDescription_AC_009_4() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\",\"description\":\"" + "x".repeat(32768) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(32767));
        verifyNoInteractions(service);

        when(service.suggest(any(), any())).thenReturn(List.of());
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\",\"description\":\"" + "x".repeat(32767) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void rejectsNulInSummaryBeforeSuggesting_AC_009_4() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\\u0000jams\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("summary"));
        verifyNoInteractions(service);
    }

    @Test
    void refusesForgeTokenAlone_AC_009_5() throws Exception {
        mvc.perform(post(PATH)
                        .with(anonymous())
                        .with(csrf())
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJmb3JnZSJ9.signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(post(PATH)
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.EMPLOYEE)
    void answers403ForEmployee_AC_009_5() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void allowsProjectManager_AC_009_5() throws Exception {
        when(service.suggest(any(), any())).thenReturn(List.of());
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void ignoresWorkspaceParameter_AC_006_10() throws Exception {
        when(service.suggest(any(), any())).thenReturn(List.of());
        String body = "{\"summary\":\" Printer \",\"description\":\" Paper stuck \"}";
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(
                        post(PATH)
                                .with(csrf())
                                .param("workspaceId", "other")
                                .header("workspaceId", "other")
                                .header("X-Workspace-Id", "other")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"summary\":\" Printer \",\"description\":\" Paper stuck \",\"workspaceId\":\"other\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        ArgumentCaptor<TicketSuggestionRequest> requests = ArgumentCaptor.forClass(TicketSuggestionRequest.class);
        verify(service, times(2)).suggest(any(), requests.capture());
        assertThat(requests.getAllValues())
                .containsExactly(
                        new TicketSuggestionRequest(" Printer ", " Paper stuck "),
                        new TicketSuggestionRequest(" Printer ", " Paper stuck "));
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void returnsResultArrayWithTicketFields_AC_009_1() throws Exception {
        UUID ticket = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID project = UUID.fromString("80000000-0000-0000-0000-000000000001");
        UUID department = UUID.fromString("90000000-0000-0000-0000-000000000001");
        when(service.suggest(any(), any()))
                .thenReturn(List.of(new TicketSearchResultResponse(
                        ticket,
                        "Printer jams",
                        new TicketProjectRef(project, "IT"),
                        new TicketDepartmentRef(department, "Support"),
                        "Remove stuck paper",
                        75,
                        OffsetDateTime.parse("2026-10-01T00:00:00+07:00"))));
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Printer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticketId").value(ticket.toString()))
                .andExpect(jsonPath("$[0].title").value("Printer jams"))
                .andExpect(jsonPath("$[0].project.projectId").value(project.toString()))
                .andExpect(jsonPath("$[0].project.name").value("IT"))
                .andExpect(jsonPath("$[0].department.departmentId").value(department.toString()))
                .andExpect(jsonPath("$[0].department.name").value("Support"))
                .andExpect(jsonPath("$[0].excerpt").value("Remove stuck paper"))
                .andExpect(jsonPath("$[0].helpfulRate").value(75))
                .andExpect(jsonPath("$[0].resolvedAt").value("2026-10-01T00:00:00+07:00"));
    }
}
