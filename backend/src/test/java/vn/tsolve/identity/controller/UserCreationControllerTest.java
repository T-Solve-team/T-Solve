package vn.tsolve.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.service.UserCreationService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UserCreationController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserCreationControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserCreationService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsAnonymous_AC_005_17(boolean create) throws Exception {
        mvc.perform(
                        create
                                ? post("/api/v1/users")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")
                                : get("/api/v1/users/creation-options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithTSolveUser
    void rejectsNonAdmin_AC_005_17(boolean create) throws Exception {
        mvc.perform(
                        create
                                ? post("/api/v1/users")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")
                                : get("/api/v1/users/creation-options"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "42", "\"email\"", "true", "", "{", "{\"email\":}", "{} {}", "{} garbage"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsNonObjectBody_AC_005_10(String body) throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG03"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409})
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersFieldErrorAndTopLevelCodeAndParams_AC_005_10_AC_005_13(int status) throws Exception {
        String code = status == 400 ? "MSG01" : "MSG117";
        String field = status == 400 ? "email" : "spaceIds";
        String param = status == 400 ? "field_name" : "space_name";
        String value = status == 400 ? "Email" : "Wiki";
        when(service.create(any()))
                .thenThrow(
                        new FieldMessageCodeException(HttpStatus.valueOf(status), code, Map.of(param, value), field));
        mvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.params." + param).value(value))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(code))
                .andExpect(jsonPath("$.fieldErrors[0].params." + param).value(value));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersMissingDomainAsUnexpectedError_AC_005_15() throws Exception {
        when(service.creationOptions()).thenThrow(new IllegalStateException());
        mvc.perform(get("/api/v1/users/creation-options"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }
}
