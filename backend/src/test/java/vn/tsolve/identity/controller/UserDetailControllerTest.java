package vn.tsolve.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.service.UserListService;
import vn.tsolve.identity.service.UserRoleUpdateService;
import vn.tsolve.identity.service.UserStatusUpdateService;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {UserDetailController.class, UserListController.class}))
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserDetailControllerTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String PATH = "/api/v1/users/" + USER;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserDetailService detailService;

    @MockBean
    private UserRoleUpdateService updateService;

    @MockBean
    private UserStatusUpdateService statusService;

    @MockBean
    private UserListService listService;

    @Test
    void rejectsAnonymousStatus_AC_006_11() throws Exception {
        mvc.perform(put(PATH + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(statusService);
    }

    @Test
    @WithTSolveUser
    void rejectsNonAdminStatus_AC_006_11() throws Exception {
        mvc.perform(put(PATH + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(statusService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "123", "00000000-0000-0000-0000-00000000001z"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsInvalidStatusId_AC_006_11(String id) throws Exception {
        mvc.perform(put("/api/v1/users/" + id + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verifyNoInteractions(statusService);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void delegatesStatusAndReturnsDetail_AC_006_11() throws Exception {
        var response = UserDetailResponse.from(
                new UserAccountResponse(
                        USER,
                        "Target",
                        "target@example.test",
                        List.of(Role.EMPLOYEE),
                        null,
                        AccountStatus.DEACTIVATED,
                        null),
                false,
                List.of(),
                List.of(),
                List.of());
        when(statusService.update(eq(USER), any(), any())).thenReturn(response);
        mvc.perform(put(PATH + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEACTIVATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER.toString()))
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
        verify(statusService).update(eq(USER), eq(new ObjectMapper().readTree("{\"status\":\"DEACTIVATED\"}")), any());
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void passesAnAbsentStatusBodyToTheServiceAsNull_AC_006_12() throws Exception {
        when(statusService.update(eq(USER), any(), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(put(PATH + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verify(statusService).update(eq(USER), eq(null), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/handover-options"})
    void rejectsAnonymous_AC_007_11(String suffix) throws Exception {
        mvc.perform(get(PATH + suffix))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(detailService, updateService);
    }

    @Test
    void rejectsAnonymousUpdate_AC_007_11() throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(detailService, updateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/handover-options"})
    @WithTSolveUser
    void rejectsNonAdmin_AC_007_11(String suffix) throws Exception {
        mvc.perform(get(PATH + suffix))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(detailService, updateService);
    }

    @Test
    @WithTSolveUser
    void rejectsNonAdminUpdate_AC_007_11() throws Exception {
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(detailService, updateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "123", "creation-options", "00000000-0000-0000-0000-00000000001z"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rejectsNonUuidBeforeService_AC_007_11(String id) throws Exception {
        mvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(get("/api/v1/users/" + id + "/handover-options"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(put("/api/v1/users/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verifyNoInteractions(detailService, updateService);
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void mapsUnknownAccount_AC_007_11() throws Exception {
        when(detailService.getDetail(eq(USER), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void checksTargetBeforeNullOrMissingUpdateBody_AC_007_20() throws Exception {
        when(updateService.update(eq(USER), any(), any())).thenThrow(new NotFoundException("MSG04", Map.of()));
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        mvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        verify(updateService, times(2)).update(eq(USER), any(), any());
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void routesUuidHandoverOptionsUnambiguously_AC_007_9() throws Exception {
        when(detailService.getHandoverOptions(eq(USER), any()))
                .thenReturn(new UserHandoverOptionsResponse(List.of(), List.of()));
        mvc.perform(get(PATH + "/handover-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects").isEmpty())
                .andExpect(jsonPath("$.spaces").isEmpty());
        verify(detailService).getHandoverOptions(eq(USER), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "42", "\"text\""})
    @WithTSolveUser(roles = Role.ADMIN)
    void passesLenientBodyForServiceShapeChecks_AC_007_12(String body) throws Exception {
        when(updateService.update(eq(USER), any(), any()))
                .thenThrow(new MessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of()) {});
        mvc.perform(put(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verify(updateService).update(eq(USER), any(), any());
    }

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void keepsDepartmentsOnListController_AC_007_11() throws Exception {
        when(listService.departments()).thenReturn(List.of());
        mvc.perform(get("/api/v1/users/departments")).andExpect(status().isOk());
        verify(listService).departments();
        verifyNoInteractions(detailService, updateService);
    }
}
