package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.PreviewRowStatus;
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
                        7L, 2, 3L, new MessageRef("MSG32", Map.of("created", 2, "skipped", 3L))));

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

    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "tickets.csv", "text/csv", "ticket_id\n".getBytes());

    private static ImportPreviewResponse preview() {
        return ImportPreviewResponse.of(42L, 1L, "tickets.csv", 2, 0, 0, new PageImpl<>(List.of()));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void uploadPassesUserProjectAndFile() throws Exception {
        when(csvImports.upload(eq("pm@company.vn"), eq(1L), any())).thenReturn(preview());

        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", "1")
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
                        .param("projectId", "1")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(csvImports);
    }

    @Test
    @WithMockUser(username = "admin@company.vn", roles = "ADMIN")
    void everyImportEndpointNeedsProjectManagerRole_AC_001_10() throws Exception {
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
    void uploadTooLargeIs413WithMsg28_AC_001_4() throws Exception {
        when(csvImports.upload(anyString(), anyLong(), any())).thenThrow(new UploadTooLargeException());

        mvc.perform(multipart("/api/v1/imports/csv")
                        .file(FILE)
                        .param("projectId", "1")
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("MSG28"));
    }

    @Test
    @WithMockUser(username = "pm@company.vn", roles = "PROJECT_MANAGER")
    void downloadsTheTemplate_AC_005_1() throws Exception {
        when(csvImports.template()).thenReturn(HEADER + "\n");

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
}
