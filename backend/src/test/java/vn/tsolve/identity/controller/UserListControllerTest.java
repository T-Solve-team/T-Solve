package vn.tsolve.identity.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.service.UserListService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UserListController.class))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserListControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserListService service;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/users/departments"})
    void rejectsAnonymous_AC_004_15(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/users/departments"})
    @WithTSolveUser
    void rejectsNonAdmin_AC_004_15(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "status", "departmentId", "q"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsInvalidInputBeforeService_AC_004_13(String parameter) throws Exception {
        var result = mvc.perform(get("/api/v1/users").param(parameter, parameter.equals("q") ? "x".repeat(101) : "bad"))
                .andExpect(status().isBadRequest());
        if (parameter.equals("q")) {
            result.andExpect(jsonPath("$.code").value("MSG02"))
                    .andExpect(jsonPath("$.params.field_name").value("Search"))
                    .andExpect(jsonPath("$.params.max_length").value(100));
        }
        verifyNoInteractions(service);
    }
}
