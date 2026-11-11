package vn.tsolve.ticket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.service.JiraImportService;

@WebMvcTest(JiraImportController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class JiraImportControllerTest {

    private static final String PM = "pm@company.vn";
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 7);
    private static final String BODY =
            "{\"projectId\":\"" + PROJECT + "\",\"resolvedFrom\":\"2026-11-01\",\"resolvedTo\":\"2026-11-07\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraImportService jiraImports;

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void pullReturnsTheJiraPreview_AC_001_1() throws Exception {
        ImportPreview preview = ImportPreview.jira(PM, PROJECT, FROM, TO, true, Instant.EPOCH);
        ReflectionTestUtils.setField(preview, "id", 42L);
        when(jiraImports.pull(PM, PROJECT, FROM, TO))
                .thenReturn(ImportPreviewResponse.of(preview, 0, 0, 0, 1, new PageImpl<>(List.of())));

        mvc.perform(post("/api/v1/imports/jira")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(42))
                .andExpect(jsonPath("$.source").value("JIRA_IMPORT"))
                .andExpect(jsonPath("$.resolvedFrom").value("2026-11-01"))
                .andExpect(jsonPath("$.needsSolutionCount").value(1))
                .andExpect(jsonPath("$.message.code").value("MSG35"));
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void jiraFailureIs502Msg50_AC_001_7() throws Exception {
        when(jiraImports.pull(any(), any(), any(), any())).thenThrow(new JiraUnavailableException("HTTP 401"));

        mvc.perform(post("/api/v1/imports/jira")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MSG50"));
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void pullNeedsProjectManagerRole_AC_001_9() throws Exception {
        mvc.perform(post("/api/v1/imports/jira")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(jiraImports);
    }
}
