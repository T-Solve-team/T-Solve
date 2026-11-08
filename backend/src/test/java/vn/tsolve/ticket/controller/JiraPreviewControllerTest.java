package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.FillSolutionRequest;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.SolutionTooLongException;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ImportPreviewService;

@WebMvcTest(ImportController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class JiraPreviewControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CsvImportService csvImports;

    @MockBean
    private ImportPreviewService previews;

    @MockBean
    private ImportConfirmService confirms;

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionNamesAllMissingSectionsAndChangesNoRow_AC_002_4() throws Exception {
        mvc.perform(
                        post("/api/v1/imports/previews/42/fill-solution")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"rowNumbers":[1,2],"symptoms":"   ","rootCause":null,
                                 "resolutionSteps":"","scope":"\\t"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(containsInAnyOrder("symptoms", "rootCause", "resolutionSteps", "scope")))
                .andExpect(jsonPath("$.fieldErrors[*].params.field_name")
                        .value(containsInAnyOrder("symptoms", "rootCause", "resolutionSteps", "scope")))
                .andExpect(jsonPath("$.fieldErrors[*].code").value(everyItem(is("MSG01"))));
        verifyNoInteractions(previews);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionRejectsSectionsOfUnicodeSpacesOnly_AC_002_4_BUG_187() throws Exception {
        mvc.perform(
                        post("/api/v1/imports/previews/42/fill-solution")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"rowNumbers":[1],"symptoms":"\\u3000","rootCause":"Bad token",
                                 "resolutionSteps":"Restart","scope":" \\u2003 "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("symptoms", "scope")));
        verifyNoInteractions(previews);
    }

    private static final String SOLUTION =
            """
            "symptoms":"Error 500","rootCause":"Bad token","resolutionSteps":"Restart","scope":"All"
            """;

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionReturnsUpdatedJiraPreview_AC_002_3() throws Exception {
        ImportPreview jira = ImportPreview.jira(
                "pm@company.vn", PROJECT, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6), false, Instant.EPOCH);
        ReflectionTestUtils.setField(jira, "id", 42L);
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any()))
                .thenReturn(ImportPreviewResponse.of(jira, 2, 0, 0, 0, new PageImpl<>(List.of())));

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1,2]," + SOLUTION + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("JIRA_IMPORT"))
                .andExpect(jsonPath("$.validCount").value(2))
                .andExpect(jsonPath("$.needsSolutionCount").value(0));

        ArgumentCaptor<FillSolutionRequest> request = ArgumentCaptor.forClass(FillSolutionRequest.class);
        verify(previews).fillSolution(eq("pm@company.vn"), eq(42L), request.capture());
        assertThat(request.getValue().rowNumbers()).containsExactly(1, 2);
        assertThat(request.getValue().symptoms()).isEqualTo("Error 500");
        assertThat(request.getValue().workaround()).isNull();
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionMapsTooLongToMsg02_AC_002_5() throws Exception {
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any()))
                .thenThrow(new SolutionTooLongException(10_000));

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.field_name").value("Solution"))
                .andExpect(jsonPath("$.params.max_length").value(10_000));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionMapsMissingPreviewToMsg04_AC_002_8() throws Exception {
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any()))
                .thenThrow(new NotFoundException("Import preview 42"));

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionMapsLostProjectAccessToMsg08_AC_002_9() throws Exception {
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any())).thenThrow(new ProjectAccessDeniedException());

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionMapsIneligibleRowsToConflict_AC_002_3() throws Exception {
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any()))
                .thenThrow(new BusinessRuleException("MSG06", Map.of()));

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionRejectsInvalidRowSelections_AC_002_3() throws Exception {
        for (String numbers : List.of("[]", "[0]", "[-1]", "[null]", "null")) {
            mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rowNumbers\":" + numbers + "," + SOLUTION + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(previews);
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void fillSolutionRequiresProjectManagerRole_AC_002_9() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(previews);
    }
}
