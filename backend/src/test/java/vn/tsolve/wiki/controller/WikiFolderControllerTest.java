package vn.tsolve.wiki.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.exception.WikiFolderInvalidParentException;
import vn.tsolve.wiki.service.WikiFolderService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WikiFolderController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiFolderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiFolderService service;

    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID FOLDER = UUID.randomUUID();
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @WithTSolveUser
    void rejectsEmployeeBeforeCreatingFolder_AC_003_10() throws Exception {
        mvc.perform(post("/api/v1/wiki/spaces/{id}/folders", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Guides\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void createsWithOptionalParentAndReturnsFolder_AC_003_1() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(new WikiFolder(FOLDER, null, "Guides", 1));
        mvc.perform(write("POST").content("{\"name\":\" Guides \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folderId").value(FOLDER.toString()))
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.name").value("Guides"))
                .andExpect(jsonPath("$.level").value(1));
        verify(service).create(USER, SPACE, new CreateWikiFolderRequest(" Guides ", null));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void passesRenameAndExplicitRootMove_AC_003_2() throws Exception {
        when(service.update(any(), any(), any())).thenReturn(new WikiFolder(FOLDER, null, "Renamed", 1));
        mvc.perform(write("PUT").content("{\"name\":\"Renamed\",\"parentId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
        verify(service).update(USER, FOLDER, new UpdateWikiFolderRequest("Renamed", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void validatesMissingBlankAndOversizedNames_AC_003_3(String method) throws Exception {
        for (String name : new String[] {"null", "\"\"", "\"   \"", "\"\u2003\u2003\""}) {
            mvc.perform(write(method).content("{\"name\":" + name + ",\"parentId\":null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        }
        mvc.perform(write(method).content("{\"parentId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        mvc.perform(write(method).content("{\"name\":\"" + "x".repeat(101) + "\",\"parentId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(100));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void checksNameLengthBeforeStrippingAndAcceptsLimit_AC_003_3() throws Exception {
        mvc.perform(write("POST").content("{\"name\":\" " + "x".repeat(100) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"));
        verifyNoInteractions(service);
        mvc.perform(write("POST").content("{\"name\":\"" + "x".repeat(100) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void requiresExplicitParentOnPut_AC_003_2() throws Exception {
        mvc.perform(write("PUT").content("{\"name\":\"Guides\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsDuplicateNameToInlineConflict_AC_003_4() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new BusinessRuleException("MSG85", Map.of(), "name"));
        mvc.perform(write("POST").content("{\"name\":\"Guides\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG85"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsDepthOrCycleToParentField_AC_003_5_AC_003_6() throws Exception {
        when(service.update(any(), any(), any())).thenThrow(new WikiFolderInvalidParentException());
        mvc.perform(write("PUT").content("{\"name\":\"Guides\",\"parentId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG86"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsUnknownFolder_AC_003_7() throws Exception {
        doThrow(new NotFoundException("MSG04", Map.of())).when(service).delete(any(), any());
        mvc.perform(write("DELETE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void deletesEmptyFolderWithoutBody_AC_003_8() throws Exception {
        mvc.perform(write("DELETE")).andExpect(status().isNoContent());
        verify(service).delete(USER, FOLDER);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsNonemptyCounts_AC_003_9() throws Exception {
        doThrow(new BusinessRuleException("MSG87", Map.of("folder_count", 2, "document_count", 3)))
                .when(service)
                .delete(any(), any());
        mvc.perform(write("DELETE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG87"))
                .andExpect(jsonPath("$.params.folder_count").value(2))
                .andExpect(jsonPath("$.params.document_count").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsOtherRolesBeforeService_AC_003_10(String method) throws Exception {
        mvc.perform(write(method).content("{\"name\":\"Guides\",\"parentId\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void rejectsKnowledgeManagerOfAnotherSpace_AC_003_10() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new AccessDeniedException("MSG08"));
        mvc.perform(write("POST").content("{\"name\":\"Guides\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void rejectsAnonymous_AC_003_10(String method) throws Exception {
        mvc.perform(write(method).content("{\"name\":\"Guides\",\"parentId\":null}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder write(String method) {
        var request =
                switch (method) {
                    case "POST" -> post("/api/v1/wiki/spaces/{id}/folders", SPACE);
                    case "PUT" -> put("/api/v1/wiki/folders/{id}", FOLDER);
                    case "DELETE" -> delete("/api/v1/wiki/folders/{id}", FOLDER);
                    default -> throw new IllegalArgumentException(method);
                };
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
