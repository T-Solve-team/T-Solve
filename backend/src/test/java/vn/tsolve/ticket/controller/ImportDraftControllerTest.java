package vn.tsolve.ticket.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportDraftSummary;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.service.ImportDraftService;

@WebMvcTest(ImportDraftController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class ImportDraftControllerTest {

    private static final String PM = "pm@company.vn";
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant SAVED = Instant.parse("2026-11-07T03:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ImportDraftService drafts;

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void saveReturnsMsg41_AC_004_1() throws Exception {
        when(drafts.save(PM, 42L)).thenReturn(new DraftSavedResponse(42L, SAVED, new MessageRef("MSG41", Map.of())));

        mvc.perform(post("/api/v1/imports/previews/42/draft").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(42))
                .andExpect(jsonPath("$.savedAt").value("2026-11-07T03:00:00Z"))
                .andExpect(jsonPath("$.message.code").value("MSG41"));
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void listReturnsTheCallersDrafts_AC_004_2() throws Exception {
        when(drafts.list(PM))
                .thenReturn(List.of(new ImportDraftSummary(42L, TicketSource.JIRA_IMPORT, PROJECT, 5, SAVED)));

        mvc.perform(get("/api/v1/imports/drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previewId").value(42))
                .andExpect(jsonPath("$[0].source").value("JIRA_IMPORT"))
                .andExpect(jsonPath("$[0].rowCount").value(5));
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void deleteIs204_AC_004_4() throws Exception {
        mvc.perform(delete("/api/v1/imports/drafts/42").with(csrf())).andExpect(status().isNoContent());
        verify(drafts).delete(PM, 42L);
    }

    @Test
    @WithMockUser(username = PM, roles = "PROJECT_MANAGER")
    void missingDraftIs404Msg04_AC_004_5() throws Exception {
        doThrow(new NotFoundException("MSG04", Map.of())).when(drafts).delete(PM, 99L);

        mvc.perform(delete("/api/v1/imports/drafts/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @WithMockUser(username = "staff@company.vn", roles = "STAFF")
    void draftsNeedProjectManagerRole_AC_004_5() throws Exception {
        mvc.perform(get("/api/v1/imports/drafts")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/imports/previews/42/draft").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(drafts);
    }
}
