package vn.tsolve.wiki.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.WikiDocumentFile;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiUploadPreview;
import vn.tsolve.wiki.entity.WikiDocumentType;
import vn.tsolve.wiki.service.WikiBrowseService;
import vn.tsolve.wiki.service.WikiSearchService;
import vn.tsolve.wiki.service.WikiUploadService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {WikiUploadController.class, WikiDocumentController.class}))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiUploadControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiUploadService uploads;

    @MockBean
    private WikiBrowseService browse;

    @MockBean
    private WikiSearchService search;

    private final UUID space = UUID.randomUUID();
    private final MockMultipartFile file =
            new MockMultipartFile("files", "guide.pdf", "application/pdf", "%PDF-".getBytes());

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void serializesPreviewAndPublishMultipartInOrder_AC_004_1_AC_004_6() throws Exception {
        when(uploads.extract(any(), eq(space), any()))
                .thenReturn(List.of(
                        new WikiUploadPreview("guide.pdf", "guide", "Text", 1, "READY", WikiDocumentType.GUIDE)));
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/uploads/extract", space)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("guide.pdf"))
                .andExpect(jsonPath("$[0].result").value("READY"))
                .andExpect(jsonPath("$[0].documentType").value("GUIDE"));
        UUID id = UUID.randomUUID();
        when(uploads.publish(any(), eq(space), any(), any())).thenReturn(List.of(new WikiDocumentRef(id, "Guide")));
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/documents", space)
                        .file(file)
                        .file(new MockMultipartFile(
                                "metadata",
                                "",
                                "application/json",
                                "{\"documents\":[{\"title\":\"Guide\",\"visibility\":\"DEPARTMENT\",\"documentType\":\"GUIDE\"}]}"
                                        .getBytes()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].documentId").value(id.toString()));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void acceptsJsonMetadataSentAsAFormStringByGeneratedClient_AC_004_6_BUG_272() throws Exception {
        String metadata =
                "{\"documents\":[{\"title\":\"Guide\",\"visibility\":\"DEPARTMENT\",\"documentType\":\"GUIDE\"}]}";
        when(uploads.publish(any(), any(), any(), any()))
                .thenReturn(List.of(new WikiDocumentRef(UUID.randomUUID(), "Guide")));
        for (String contentType : List.of("text/plain", "application/octet-stream")) {
            mvc.perform(multipart("/api/v1/wiki/spaces/{id}/documents", space)
                            .file(file)
                            .file(new MockMultipartFile(
                                    "metadata", "", contentType, metadata.getBytes(StandardCharsets.UTF_8)))
                            .with(csrf()))
                    .andExpect(status().isCreated());
        }
        verify(uploads, org.mockito.Mockito.times(2)).publish(any(), eq(space), eq(List.of(file)), eq(metadata));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsMissingPartsAndOversizeWithContractCodes_AC_004_3_AC_004_5_AC_004_10_BUG_269() throws Exception {
        when(uploads.extract(any(), any(), any()))
                .thenThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "files"), "files"));
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/uploads/extract", space).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("files"));
        when(uploads.publish(any(), any(), any(), eq(null)))
                .thenThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "metadata"), "metadata"));
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/documents", space)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("metadata"));
        org.mockito.Mockito.doThrow(
                        new org.springframework.web.multipart.MaxUploadSizeExceededException(10 * 1024 * 1024))
                .when(uploads)
                .extract(any(), any(), any());
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/uploads/extract", space)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("MSG28"));
    }

    @Test
    @WithTSolveUser
    void deniesNonManagerRole_AC_004_17() throws Exception {
        for (String suffix : List.of("uploads/extract", "documents")) {
            mvc.perform(multipart("/api/v1/wiki/spaces/" + space + "/" + suffix)
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"));
        }
        verifyNoInteractions(uploads);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void deniesManagerOfAnotherSpace_AC_004_17() throws Exception {
        when(uploads.extract(any(), any(), any())).thenThrow(new AccessDeniedException("MSG08"));
        when(uploads.publish(any(), any(), any(), any())).thenThrow(new AccessDeniedException("MSG08"));
        for (String suffix : List.of("uploads/extract", "documents")) {
            mvc.perform(multipart("/api/v1/wiki/spaces/" + space + "/" + suffix)
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"));
        }
    }

    @Test
    @WithTSolveUser
    void streamsOriginalWithSafeInternationalFilenameAndNosniff_AC_002_12() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-original".getBytes(StandardCharsets.UTF_8);
        when(uploads.download(any(), eq(id)))
                .thenReturn(new WikiDocumentFile(
                        "Hướng dẫn\r\nInjected.pdf", "application/pdf", bytes.length, out -> out.write(bytes)));
        var pending = mvc.perform(get("/api/v1/wiki/documents/{id}/file", id))
                .andExpect(request().asyncStarted())
                .andReturn();
        var result = mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        String header = result.getResponse().getHeader("Content-Disposition");
        assertThat(header)
                .startsWith("attachment;")
                .contains("filename*=UTF-8''")
                .doesNotContain("\r", "\n");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("Hướng dẫnInjected.pdf");
    }

    @Test
    @WithTSolveUser
    void hidesUnavailableDownload_AC_002_12_AC_002_13() throws Exception {
        when(uploads.download(any(), any())).thenThrow(new NotFoundException("MSG110", Map.of()));
        mvc.perform(get("/api/v1/wiki/documents/{id}/file", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG110"));
    }

    @Test
    void rejectsAnonymous_AC_004_17_AC_002_12() throws Exception {
        mvc.perform(multipart("/api/v1/wiki/spaces/{id}/uploads/extract", space)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(get("/api/v1/wiki/documents/{id}/file", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(uploads);
    }
}
