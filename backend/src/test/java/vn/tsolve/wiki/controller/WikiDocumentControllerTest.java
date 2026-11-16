package vn.tsolve.wiki.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.service.WikiBrowseService;
import vn.tsolve.wiki.service.WikiSearchService;
import vn.tsolve.wiki.service.WikiUploadService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WikiDocumentController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiDocumentControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiBrowseService browse;

    @MockBean
    private WikiSearchService search;

    @MockBean
    private WikiUploadService uploads;

    @Test
    @WithTSolveUser
    void searchesAndSerializesSummary_AC_002_7_AC_002_10() throws Exception {
        UUID id = UUID.randomUUID();
        when(search.search(any(), eq("guide")))
                .thenReturn(List.of(new WikiDocumentSummary(
                        id,
                        "Guide",
                        UUID.randomUUID(),
                        "Support Wiki",
                        null,
                        null,
                        "COMPANY_WIDE",
                        "Owner Name",
                        Instant.EPOCH)));
        mvc.perform(get("/api/v1/wiki/documents/search").param("q", "guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(id.toString()))
                .andExpect(jsonPath("$[0].title").value("Guide"))
                .andExpect(jsonPath("$[0].spaceId").isString())
                .andExpect(jsonPath("$[0].spaceName").value("Support Wiki"))
                .andExpect(jsonPath("$[0].folderId").isEmpty())
                .andExpect(jsonPath("$[0].folderName").isEmpty())
                .andExpect(jsonPath("$[0].visibility").value("COMPANY_WIDE"))
                .andExpect(jsonPath("$[0].owner").value("Owner Name"))
                .andExpect(jsonPath("$[0].updatedAt").value("1970-01-01T00:00:00Z"));
    }

    @Test
    @WithTSolveUser
    void mapsMissingAndOverlengthQueryFieldErrors_AC_002_9_BUG_253() throws Exception {
        doThrow(new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "q"), "q"))
                .when(search)
                .search(any(), eq(null));
        doThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", "q", "max_length", 200), "q"))
                .when(search)
                .search(any(), eq("a".repeat(201)));
        mvc.perform(get("/api/v1/wiki/documents/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        mvc.perform(get("/api/v1/wiki/documents/search").param("q", "a".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.max_length").value(200));
        verify(search).search(any(), eq(null));
    }

    @Test
    @WithTSolveUser
    void readsPlainTextAndFileMetadata_AC_002_11() throws Exception {
        UUID id = UUID.randomUUID();
        when(browse.getDocument(any(), eq(id)))
                .thenReturn(new WikiDocumentDetail(
                        id,
                        "Guide",
                        UUID.randomUUID(),
                        "Wiki",
                        null,
                        null,
                        "DEPARTMENT",
                        "Owner",
                        Instant.EPOCH,
                        "<script>plain text</script>",
                        "guide.pdf",
                        42,
                        true));
        mvc.perform(get("/api/v1/wiki/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(id.toString()))
                .andExpect(jsonPath("$.content").value("<script>plain text</script>"))
                .andExpect(jsonPath("$.fileName").value("guide.pdf"))
                .andExpect(jsonPath("$.fileSize").value(42))
                .andExpect(jsonPath("$.canDelete").value(true));
    }

    @Test
    @WithTSolveUser
    void hidesUnknownAndNonViewableDocuments_AC_002_13() throws Exception {
        when(browse.getDocument(any(), any())).thenThrow(new NotFoundException("MSG110", Map.of()));
        mvc.perform(get("/api/v1/wiki/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG110"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"search?q=guide", "00000000-0000-0000-0000-000000000001"})
    void rejectsAnonymous_AC_002_7_AC_002_11(String path) throws Exception {
        mvc.perform(get("/api/v1/wiki/documents/" + path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(browse, search);
    }
}
