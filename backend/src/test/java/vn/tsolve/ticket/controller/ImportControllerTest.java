package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.ticket.dto.FillSolutionRequest;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.UploadTooLargeException;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ImportPreviewService;

@WebMvcTest(ImportController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class ImportControllerTest {

    private static final String HEADER =
            "ticket_id,title,description,author,resolved_date,symptoms,root_cause,resolution_steps,workaround,scope";

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
    void confirmReturnsTheImportResult_AC_004_4() throws Exception {
        when(confirms.confirm("pm@company.vn", 42L))
                .thenReturn(new ImportResultResponse(
                        7L, 2, 3L, new MessageRef("MSG32", Map.of("created", 2, "skipped", 3L)), null));

        mvc.perform(post("/api/v1/imports/previews/42/confirm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importBatchId").value(7))
                .andExpect(jsonPath("$.message.code").value("MSG32"))
                .andExpect(jsonPath("$.message.params.created").value(2));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void confirmFailureIsMsg33_AC_004_7() throws Exception {
        when(confirms.confirm("pm@company.vn", 42L)).thenThrow(new ImportFailedException(HttpStatus.CONFLICT));

        mvc.perform(post("/api/v1/imports/previews/42/confirm").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG33"));
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void confirmNeedsProjectManagerRole_AC_001_10() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/confirm").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(confirms);
    }

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "tickets.csv", "text/csv", "ticket_id\n".getBytes());

    private static ImportPreviewResponse preview() {
        return preview(ImportType.WITH_SOLUTION);
    }

    private static ImportPreviewResponse preview(ImportType importType) {
        ImportPreview preview =
                new ImportPreview("pm@company.vn", PROJECT, TicketSource.CSV, importType, "tickets.csv", Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", 42L);
        return ImportPreviewResponse.of(preview, 2, 0, 0, 0, new PageImpl<>(List.of()));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void readsThePreviewOfTheCaller_AC_007_1() throws Exception {
        when(previews.preview("pm@company.vn", 42L)).thenReturn(preview());

        mvc.perform(get("/api/v1/imports/previews/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(42))
                .andExpect(jsonPath("$.projectId").value(PROJECT.toString()))
                .andExpect(jsonPath("$.validCount").value(2));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadWithoutATypeIsAFileWithSolutions_AC_012_1() throws Exception {
        when(csvImports.upload(eq("pm@company.vn"), eq(PROJECT), eq(ImportType.WITH_SOLUTION), any()))
                .thenReturn(preview());

        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", PROJECT.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(42))
                .andExpect(jsonPath("$.validCount").value(2))
                .andExpect(jsonPath("$.rows.page.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void uploadNeedsProjectManagerRole_AC_001_10() throws Exception {
        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", PROJECT.toString())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(csvImports);
    }

    @Test
    @WithMockUser(username = "admin@company.vn", roles = "ADMIN")
    void everyImportEndpointNeedsProjectManagerRole_AC_001_10() throws Exception {
        mvc.perform(get("/api/v1/imports/previews/42")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/imports/previews/42/rows")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/imports/previews/42/rows/1")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/imports/previews/42/remove-rows")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(csvImports, previews);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadRejectsAProjectIdThatIsNotAUuid() throws Exception {
        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", "1")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
        verifyNoInteractions(csvImports);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadTooLargeIs413WithMsg28_AC_001_4() throws Exception {
        when(csvImports.upload(anyString(), any(), any(), any())).thenThrow(new UploadTooLargeException());

        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", PROJECT.toString())
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("MSG28"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadPassesTheChosenType_AC_012_1() throws Exception {
        when(csvImports.upload(eq("pm@company.vn"), eq(PROJECT), eq(ImportType.WITHOUT_SOLUTION), any()))
                .thenReturn(preview());

        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", PROJECT.toString())
                        .param("importType", "WITHOUT_SOLUTION")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(42))
                .andExpect(jsonPath("$.importType").value("WITH_SOLUTION"))
                .andExpect(jsonPath("$.needsSolutionCount").value(0));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadWithAnUnknownTypeIsRejected_AC_012_1() throws Exception {
        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", PROJECT.toString())
                        .param("importType", "SOMETHING")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(csvImports);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void downloadsTheTemplateOfTheChosenType_AC_012_2() throws Exception {
        when(csvImports.template(ImportType.WITHOUT_SOLUTION)).thenReturn("ticket_id,title\n");

        mvc.perform(get("/api/v1/imports/csv/template").param("importType", "WITHOUT_SOLUTION"))
                .andExpect(status().isOk())
                .andExpect(content().string("ticket_id,title\n"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void downloadsTheTemplate_AC_005_1() throws Exception {
        when(csvImports.template(ImportType.WITH_SOLUTION)).thenReturn(HEADER + "\n");

        mvc.perform(get("/api/v1/imports/csv/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(
                        header().string("Content-Disposition", "attachment; filename=\"ticket-import-template.csv\""))
                .andExpect(content().string(HEADER + "\n"));
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void templateNeedsProjectManagerRole_AC_005_2() throws Exception {
        mvc.perform(get("/api/v1/imports/csv/template")).andExpect(status().isForbidden());
        verifyNoInteractions(csvImports);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void rowsTakeStatusAndPaging() throws Exception {
        when(previews.rows(eq("pm@company.vn"), eq(42L), eq(PreviewRowStatus.ERROR), any()))
                .thenReturn(new PagedModel<>(new PageImpl<>(List.of())));

        mvc.perform(get("/api/v1/imports/previews/42/rows")
                        .param("status", "ERROR")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(previews).rows(eq("pm@company.vn"), eq(42L), eq(PreviewRowStatus.ERROR), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void removeRowsRejectsEmptyList() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/remove-rows")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        verifyNoInteractions(previews);
    }

    private static final String SOLUTION =
            "\"symptoms\":\"Error 500\",\"rootCause\":\"Bad token\",\"resolutionSteps\":\"Restart\",\"scope\":\"All\"";

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionPassesTheRequestToTheService_AC_013_1() throws Exception {
        when(previews.fillSolution(eq("pm@company.vn"), eq(42L), any()))
                .thenReturn(preview(ImportType.WITHOUT_SOLUTION));

        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1,2]," + SOLUTION + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validCount").value(2))
                .andExpect(jsonPath("$.importType").value("WITHOUT_SOLUTION"));

        ArgumentCaptor<FillSolutionRequest> request = ArgumentCaptor.forClass(FillSolutionRequest.class);
        verify(previews).fillSolution(eq("pm@company.vn"), eq(42L), request.capture());
        assertThat(request.getValue().rowNumbers()).containsExactly(1, 2);
        assertThat(request.getValue().workaround()).isNull();
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionRejectsEachEmptyRequiredSectionWithMsg01_AC_013_2() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"rowNumbers\":[1],\"symptoms\":\"   \",\"resolutionSteps\":\"\",\"scope\":\"All\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(containsInAnyOrder("symptoms", "rootCause", "resolutionSteps")))
                .andExpect(jsonPath("$.fieldErrors[*].code").value(everyItem(is("MSG01"))));
        verifyNoInteractions(previews);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionRejectsASectionOfOnlyUnicodeSpacesWithMsg01_BUG_151() throws Exception {
        // U+3000 and U+2003 are not removed by String.trim (what @NotBlank checks), but are by strip
        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1],\"symptoms\":\"　 \",\"rootCause\":\"a\","
                                + "\"resolutionSteps\":\"b\",\"scope\":\"All\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("symptoms")));
        verifyNoInteractions(previews);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionRejectsAnEmptyRowList() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[]," + SOLUTION + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(previews);
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void fillSolutionThatCannotApplyIs409WithMsg06_AC_013_3() throws Exception {
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
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void fillSolutionNeedsProjectManagerRole_AC_013_5() throws Exception {
        mvc.perform(post("/api/v1/imports/previews/42/fill-solution")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowNumbers\":[1]," + SOLUTION + "}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(previews);
    }
}
