package vn.tsolve.wiki.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.WikiRestoreResult;
import vn.tsolve.wiki.dto.WikiTrashItem;
import vn.tsolve.wiki.service.WikiBrowseService;
import vn.tsolve.wiki.service.WikiSearchService;
import vn.tsolve.wiki.service.WikiTrashService;
import vn.tsolve.wiki.service.WikiUploadService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "vn.tsolve.wiki.controller.Wiki(Document|Trash)Controller"))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiTrashControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiBrowseService browse;

    @MockBean
    private WikiSearchService search;

    @MockBean
    private WikiUploadService uploads;

    @MockBean
    private WikiTrashService trash;

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID = UUID.randomUUID();

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void trashesPublishedDocument_AC_005_1() throws Exception {
        mvc.perform(delete("/api/v1/wiki/documents/{id}", ID).with(csrf())).andExpect(status().isNoContent());
        verify(trash).trash(USER, ID);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void returnsTrashWithFullNamesAndNullableFolder_AC_005_3() throws Exception {
        when(trash.listTrash(USER, ID))
                .thenReturn(List.of(new WikiTrashItem(ID, "Guide", null, "Owner Name", "Manager Name", Instant.EPOCH)));
        mvc.perform(get("/api/v1/wiki/spaces/{id}/trash", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Guide"))
                .andExpect(jsonPath("$[0].folderName").isEmpty())
                .andExpect(jsonPath("$[0].owner").value("Owner Name"))
                .andExpect(jsonPath("$[0].deletedBy").value("Manager Name"))
                .andExpect(jsonPath("$[0].deletedAt").value("1970-01-01T00:00:00Z"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void restoresWithFinalTitleAndRenameFlag_AC_005_5_AC_005_6() throws Exception {
        when(trash.restore(USER, ID)).thenReturn(new WikiRestoreResult("Guide (restored)", true));
        mvc.perform(post("/api/v1/wiki/documents/{id}/restore", ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Guide (restored)"))
                .andExpect(jsonPath("$.renamed").value(true));
        verify(trash).restore(USER, ID);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void permanentlyDeletesWithNoContent_AC_005_8() throws Exception {
        mvc.perform(delete("/api/v1/wiki/documents/{id}/permanent", ID).with(csrf()))
                .andExpect(status().isNoContent());
        verify(trash).deletePermanently(USER, ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRASH", "RESTORE", "PERMANENT"})
    @WithTSolveUser
    void preservesVisibilitySensitiveErrorsForNonManagers_AC_005_9_AC_005_10_BUG_280(String operation)
            throws Exception {
        stubFailure(operation, new AccessDeniedException("MSG08"));
        mvc.perform(request(operation).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        stubFailure(operation, new NotFoundException("MSG110", Map.of()));
        mvc.perform(request(operation).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG110"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsRestoreTitleRaceToConflict_AC_005_17() throws Exception {
        when(trash.restore(any(), any())).thenThrow(new BusinessRuleException("MSG84", Map.of()));
        mvc.perform(request("RESTORE").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG84"));
    }

    @Test
    @WithTSolveUser
    void letsServiceAnswerEmployeeTrashListing_AC_002_6_AC_005_10_BUG_303() throws Exception {
        when(trash.listTrash(USER, ID)).thenThrow(new AccessDeniedException("MSG08"));
        mvc.perform(request("LIST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        doThrow(new NotFoundException("MSG04", Map.of())).when(trash).listTrash(USER, ID);
        mvc.perform(request("LIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRASH", "LIST", "RESTORE", "PERMANENT"})
    void rejectsAnonymous_AC_005_10(String operation) throws Exception {
        mvc.perform(request(operation).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(trash);
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "TRASH" -> delete("/api/v1/wiki/documents/{id}", ID);
            case "RESTORE" -> post("/api/v1/wiki/documents/{id}/restore", ID);
            case "PERMANENT" -> delete("/api/v1/wiki/documents/{id}/permanent", ID);
            case "LIST" -> get("/api/v1/wiki/spaces/{id}/trash", ID);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private void stubFailure(String operation, RuntimeException error) {
        switch (operation) {
            case "TRASH" -> doThrow(error).when(trash).trash(any(), any());
            case "RESTORE" -> doThrow(error).when(trash).restore(any(), any());
            case "PERMANENT" -> doThrow(error).when(trash).deletePermanently(any(), any());
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
