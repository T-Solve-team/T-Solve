package vn.tsolve.wiki.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.service.WikiBrowseService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WikiBrowseController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiBrowseControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiBrowseService service;

    private static final UUID SPACE = UUID.randomUUID();

    @Test
    @WithTSolveUser
    void listsSpacesWithAccessLevel_AC_002_1() throws Exception {
        when(service.listSpaces(any())).thenReturn(List.of(new WikiSpace(SPACE, "Wiki", "Support", "VIEW")));
        mvc.perform(get("/api/v1/wiki/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(SPACE.toString()))
                .andExpect(jsonPath("$[0].departmentName").value("Support"))
                .andExpect(jsonPath("$[0].accessLevel").value("VIEW"));
        verify(service).listSpaces(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @WithTSolveUser
    void listsCompanyWideDocuments_AC_002_2() throws Exception {
        when(service.listCompanyWide(any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/wiki/company-wide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithTSolveUser
    void listsFlatFoldersWithNullParent_AC_002_3() throws Exception {
        UUID folder = UUID.randomUUID();
        when(service.listFolders(any(), eq(SPACE))).thenReturn(List.of(new WikiFolder(folder, null, "Guides", 1)));
        mvc.perform(get("/api/v1/wiki/spaces/{id}/folders", SPACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].folderId").value(folder.toString()))
                .andExpect(jsonPath("$[0].parentId").isEmpty())
                .andExpect(jsonPath("$[0].level").value(1));
    }

    @Test
    @WithTSolveUser
    void passesOptionalFolderId_AC_002_4() throws Exception {
        UUID folder = UUID.randomUUID();
        mvc.perform(get("/api/v1/wiki/spaces/{id}/documents", SPACE)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/wiki/spaces/{id}/documents", SPACE).param("folderId", folder.toString()))
                .andExpect(status().isOk());
        verify(service).listDocuments(any(), eq(SPACE), eq(null));
        verify(service).listDocuments(any(), eq(SPACE), eq(folder));
    }

    @ParameterizedTest
    @ValueSource(strings = {"folders", "documents"})
    @WithTSolveUser
    void rejectsNonViewer_AC_002_5(String path) throws Exception {
        when(service.listFolders(any(), any())).thenThrow(new AccessDeniedException("MSG08"));
        when(service.listDocuments(any(), any(), any())).thenThrow(new AccessDeniedException("MSG08"));
        mvc.perform(get("/api/v1/wiki/spaces/{id}/" + path, SPACE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    @WithTSolveUser
    void mapsMissingSpaceOrFolder_AC_002_6() throws Exception {
        when(service.listDocuments(any(), any(), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(get("/api/v1/wiki/spaces/{id}/documents", SPACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/spaces",
                "/company-wide",
                "/spaces/00000000-0000-0000-0000-000000000001/folders",
                "/spaces/00000000-0000-0000-0000-000000000001/documents"
            })
    void rejectsAnonymous_AC_002_1_AC_002_2_AC_002_3_AC_002_4(String path) throws Exception {
        mvc.perform(get("/api/v1/wiki" + path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }
}
