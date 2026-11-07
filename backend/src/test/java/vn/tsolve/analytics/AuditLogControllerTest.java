package vn.tsolve.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.analytics.controller.AuditLogController;
import vn.tsolve.analytics.exception.InvalidAuditFilterException;
import vn.tsolve.analytics.service.AuditLogService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.WithTSolveUser;

@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuditLogControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuditLogService service;

    @Test
    @WithTSolveUser(roles = Role.ADMIN)
    void servesAdminWithDefaultPaging_AC_003_1() throws Exception {
        when(service.findEntries(null, null, null, null, 0, 20, List.of())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithTSolveUser
    void refusesNonAdmin_AC_003_8() throws Exception {
        mvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(service);
    }

    @Test
    void refusesAnonymous_AC_003_8() throws Exception {
        mvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MSG39", "MSG06"})
    @WithTSolveUser(roles = Role.ADMIN)
    void rendersFilterProblem_AC_003_6_AC_003_7(String code) throws Exception {
        when(service.findEntries(any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenThrow(new InvalidAuditFilterException(code));
        mvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }
}
