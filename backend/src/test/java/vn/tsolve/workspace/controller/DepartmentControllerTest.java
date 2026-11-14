package vn.tsolve.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.service.DepartmentService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DepartmentController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DepartmentControllerTest {
    private static final String FIFTY_ONE_EM_SPACES = "{\"name\":\""
            + "\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003"
            + "\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003"
            + "\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003"
            + "\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003\\u2003"
            + "\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DepartmentService service;

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void createsWithFiftyCharacterName_AC_001_1() throws Exception {
        String name = "a".repeat(50);
        UUID id = UUID.randomUUID();
        when(service.create(new CreateDepartmentRequest(name))).thenReturn(new DepartmentRefResponse(id, name));
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentId").value(id.toString()))
                .andExpect(jsonPath("$.name").value(name));
        verify(service).create(new CreateDepartmentRequest(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"name\":null}",
                "{\"name\":\"\"}",
                "{\"name\":\"   \"}",
                "{\"name\":\"                                                   \"}",
                "{\"name\":\"\\u3000\"}",
                "{\"name\":\"\\u2003\"}",
                FIFTY_ONE_EM_SPACES
            })
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsMissingOrBlankName_AC_001_5_AC_001_6(String body) throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"))
                .andExpect(jsonPath("$.params.field_name").value("name"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsFiftyOneNonBlankCharacters_AC_001_6() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.max_length").value(50))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser
    void rejectsNonAdmin_AC_001_12() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Support\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymous_AC_001_13() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Support\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersUnexpectedFailure_AC_001_11() throws Exception {
        when(service.create(any())).thenThrow(new RuntimeException());
        mvc.perform(post("/api/v1/departments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Support\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final String PATH = "/api/v1/departments/" + ID;

    private static MockHttpServletRequestBuilder rename(String path, String body) {
        return put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void readsDepartment_AC_003_1() throws Exception {
        when(service.get(ID)).thenReturn(new DepartmentRefResponse(ID, "IT"));
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("IT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12345678-1234-1234-1234-12345678901", "00000000-0000-4000-8000-0000000000d1x"})
    @WithTSolveUser(roles = Role.ADMIN)
    void answersNotFoundForMalformedId_AC_003_2(String id) throws Exception {
        mvc.perform(get("/api/v1/departments/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(rename("/api/v1/departments/" + id, "{\"name\":\"IT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void answersNotFoundWhenServiceFindsNone_AC_003_2() throws Exception {
        when(service.get(ID)).thenThrow(new NotFoundException("MSG04", Map.of()));
        when(service.update(any(), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(rename(PATH, "{\"name\":\"IT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void renamesWithFiftyCharacterName_AC_003_3() throws Exception {
        String name = "a".repeat(50);
        when(service.update(ID, new UpdateDepartmentRequest(name))).thenReturn(new DepartmentRefResponse(ID, name));
        mvc.perform(rename(PATH, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(ID.toString()))
                .andExpect(jsonPath("$.name").value(name));
        verify(service).update(ID, new UpdateDepartmentRequest(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"name\":null}",
                "{\"name\":\"\"}",
                "{\"name\":\"   \"}",
                "{\"name\":\"                                                   \"}",
                "{\"name\":\"\\u3000\"}",
                "{\"name\":\"\\u2003\"}",
                FIFTY_ONE_EM_SPACES
            })
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsMissingOrBlankRename_AC_003_8(String body) throws Exception {
        mvc.perform(rename(PATH, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.params.field_name").value("name"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsFiftyOneNonBlankCharactersOnRename_AC_003_9() throws Exception {
        mvc.perform(rename(PATH, "{\"name\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.max_length").value(50))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        verifyNoInteractions(service);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void answersConflictOnTakenName_AC_003_10() throws Exception {
        when(service.update(any(), any())).thenThrow(new BusinessRuleException("MSG43", Map.of(), "name"));
        mvc.perform(rename(PATH, "{\"name\":\"HR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG43"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersUnexpectedRenameFailure_AC_003_17() throws Exception {
        when(service.update(any(), any())).thenThrow(new IllegalStateException("no Space"));
        mvc.perform(rename(PATH, "{\"name\":\"HR\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithTSolveUser
    void rejectsNonAdminReadAndRename_AC_003_18() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        mvc.perform(rename(PATH, "{\"name\":\"IT Support\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousReadAndRename_AC_003_19() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(rename(PATH, "{\"name\":\"IT Support\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }
}
