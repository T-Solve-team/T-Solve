package vn.tsolve.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
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
}
