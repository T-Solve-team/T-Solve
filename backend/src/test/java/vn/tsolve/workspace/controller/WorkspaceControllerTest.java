package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.service.WorkspaceService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WorkspaceController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WorkspaceService service;

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-005.1 Admin can PUT Workspace information")
    void updatesWorkspace() throws Exception {
        when(service.update(any()))
                .thenReturn(new WorkspaceInfoResponse("Renamed", "https://fake.atlassian.net", "fake@example.test"));
        mvc.perform(put("/api/v1/workspace")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Renamed\",\"serviceAccountEmail\":\"fake@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Renamed"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"))
                .andExpect(jsonPath("$.serviceAccountEmail").value("fake@example.test"))
                .andExpect(jsonPath("$.serviceAccountToken").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "42", "{", "{} {}", "{} garbage"})
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-005.3 malformed PUT bodies return safe MSG03 before calling service")
    void rejectsMalformedUpdate(String body) throws Exception {
        mvc.perform(put("/api/v1/workspace")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG03"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-005.5 failed update check renders MSG156 on serviceAccountToken")
    void rendersUpdateFailure() throws Exception {
        when(service.update(any()))
                .thenThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG156", Map.of("tracker_name", "Jira"), "serviceAccountToken"));
        mvc.perform(put("/api/v1/workspace")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG156"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("serviceAccountToken"));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-005.9 PUT before setup renders 404 MSG60")
    void rendersMissingUpdateWorkspace() throws Exception {
        when(service.update(any())).thenThrow(new NotFoundException("MSG60", Map.of()));
        mvc.perform(put("/api/v1/workspace")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-005.10 non-Admin PUT returns 403 without calling service")
    void rejectsNonAdminUpdate() throws Exception {
        mvc.perform(put("/api/v1/workspace")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-003.1 setup returns 201 with Workspace information")
    void createsWorkspace() throws Exception {
        when(service.setup(any()))
                .thenReturn(
                        new WorkspaceInfoResponse("Fake company", "https://fake.atlassian.net", "fake@example.test"));
        mvc.perform(post("/api/v1/workspace/setup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Fake company"));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-003.2 renders field errors and their parameters as Problem Details")
    void rendersFieldError() throws Exception {
        when(service.setup(any()))
                .thenThrow(new FieldMessageCodeException(
                        HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", "Company name"), "companyName"));
        mvc.perform(post("/api/v1/workspace/setup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.params.field_name").value("Company name"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("companyName"))
                .andExpect(jsonPath("$.fieldErrors[0].params.field_name").value("Company name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "42", "{", "{} {}", "{} garbage"})
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-003.2 malformed requests return MSG03 without exposing input or calling service")
    void rejectsMalformedBody(String body) throws Exception {
        mvc.perform(post("/api/v1/workspace/setup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG03"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-003.11 duplicate setup renders 409 WORKSPACE_ALREADY_SET_UP")
    void rendersConflict() throws Exception {
        when(service.setup(any())).thenThrow(new BusinessRuleException("WORKSPACE_ALREADY_SET_UP", Map.of()));
        mvc.perform(post("/api/v1/workspace/setup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ALREADY_SET_UP"));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-003.13 non-Admin cannot set up the Workspace")
    void rejectsNonAdminSetup() throws Exception {
        mvc.perform(post("/api/v1/workspace/setup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-004.2 GET returns 200 and exactly the three public Workspace fields")
    void readsWorkspace() throws Exception {
        when(service.get())
                .thenReturn(
                        new WorkspaceInfoResponse("Fake company", "https://fake.atlassian.net", "fake@example.test"));
        String body = mvc.perform(get("/api/v1/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Fake company"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"))
                .andExpect(jsonPath("$.serviceAccountEmail").value("fake@example.test"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .size())
                .isEqualTo(3);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    @DisplayName("AC-004.3 missing Workspace renders 404 MSG60")
    void rendersMissingWorkspace() throws Exception {
        when(service.get()).thenThrow(new NotFoundException("MSG60", Map.of()));
        mvc.perform(get("/api/v1/workspace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-004.4 non-Admin cannot read Workspace information")
    void rejectsNonAdminRead() throws Exception {
        mvc.perform(get("/api/v1/workspace"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }
}
