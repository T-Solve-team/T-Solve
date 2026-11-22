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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.service.WikiPermissionService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WikiSpaceSettingsController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WikiSpaceSettingsControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WikiPermissionService service;

    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID PERMISSION = UUID.randomUUID();
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void returnsHeaderAndComputedLockedDepartment_AC_006_1() throws Exception {
        when(service.getSettings(USER, SPACE))
                .thenReturn(new WikiSpaceSettings(
                        SPACE,
                        "Support Wiki",
                        "Support",
                        List.of("Manager"),
                        new WikiSpaceSettings.OwnDepartment(UUID.randomUUID(), "Support", true),
                        List.of(),
                        List.of()));
        mvc.perform(request("SETTINGS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId").value(SPACE.toString()))
                .andExpect(jsonPath("$.knowledgeManagers[0]").value("Manager"))
                .andExpect(jsonPath("$.ownDepartment.locked").value(true))
                .andExpect(jsonPath("$.departments").isEmpty())
                .andExpect(jsonPath("$.users").isEmpty());
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void returnsCandidates_AC_006_2() throws Exception {
        when(service.listCandidates(USER, SPACE))
                .thenReturn(new WikiPermissionCandidates(
                        List.of(new WikiPermissionCandidates.Department(UUID.randomUUID(), "Finance")), List.of()));
        mvc.perform(request("CANDIDATES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0].name").value("Finance"));
        verify(service).listCandidates(USER, SPACE);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void grantsWithNoResponseBody_AC_006_3() throws Exception {
        UUID department = UUID.randomUUID();
        mvc.perform(request("GRANT").content("{\"departmentIds\":[\"" + department + "\"],\"userIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
        verify(service).grant(USER, SPACE, new GrantWikiPermissionsRequest(List.of(department), List.of()));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsEmptySelectionToGranteesField_AC_006_4() throws Exception {
        doThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "grantees"), "grantees"))
                .when(service)
                .grant(any(), any(), any());
        mvc.perform(request("GRANT").content("{\"departmentIds\":[],\"userIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("grantees"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"{}", "{\"departmentIds\":null,\"userIds\":[]}", "{\"departmentIds\":[null],\"userIds\":[]}"})
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void validatesRequiredListsAndIds_AC_006_4(String body) throws Exception {
        mvc.perform(request("GRANT").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsInvalidGrantee_AC_006_5() throws Exception {
        doThrow(new NotFoundException("MSG04", Map.of())).when(service).grant(any(), any(), any());
        mvc.perform(request("GRANT").content("{\"departmentIds\":[],\"userIds\":[\"" + USER + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void revokesWithNoResponseBody_AC_006_6() throws Exception {
        mvc.perform(request("REVOKE"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).revoke(USER, PERMISSION);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsUnknownOrForeignPermission_AC_006_7() throws Exception {
        doThrow(new NotFoundException("MSG04", Map.of())).when(service).revoke(USER, PERMISSION);
        mvc.perform(request("REVOKE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SETTINGS", "CANDIDATES", "GRANT", "REVOKE"})
    @WithTSolveUser(roles = {Role.EMPLOYEE, Role.ADMIN})
    void deniesNonManagersOnEveryEndpoint_AC_006_8(String operation) throws Exception {
        mvc.perform(request(operation).content("{\"departmentIds\":[],\"userIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.KNOWLEDGE_MANAGER)
    void mapsManagerScopeDenial_AC_006_8() throws Exception {
        when(service.getSettings(USER, SPACE)).thenThrow(new AccessDeniedException("MSG08"));
        mvc.perform(request("SETTINGS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SETTINGS", "CANDIDATES", "GRANT", "REVOKE"})
    void requiresSignInOnEveryEndpoint_AC_006_8(String operation) throws Exception {
        mvc.perform(request(operation).content("{\"departmentIds\":[],\"userIds\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder request(String operation) {
        var request =
                switch (operation) {
                    case "SETTINGS" -> get("/api/v1/wiki/spaces/{id}/settings", SPACE);
                    case "CANDIDATES" -> get("/api/v1/wiki/spaces/{id}/permission-candidates", SPACE);
                    case "GRANT" -> post("/api/v1/wiki/spaces/{id}/permissions", SPACE);
                    case "REVOKE" -> delete("/api/v1/wiki/permissions/{id}", PERMISSION);
                    default -> throw new IllegalArgumentException(operation);
                };
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
