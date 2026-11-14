package vn.tsolve.ticket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;
import vn.tsolve.ticket.service.ManualEntryService;

@WebMvcTest(ManualEntryController.class)
@Import(MethodSecurityConfig.class)
@TestPropertySource(properties = {"tsolve.cors.allowed-origins=http://localhost:5173", "server.port=0"})
class ManualEntryControllerTest {

    private static final String PM_ID = "00000000-0000-0000-0000-0000000000aa";
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String ENTRY = "{\"ticketId\":\"T-1\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ManualEntryService manualEntries;

    @Test
    @WithTSolveUser(id = PM_ID, fullName = "Nguyen Van An", roles = Role.PROJECT_MANAGER)
    void saveAnswers201WithTheTicketIdAndMsg32_AC_006_1() throws Exception {
        when(manualEntries.save(any(), eq(ENTRY), eq(List.of())))
                .thenReturn(
                        new ManualEntryResponse(TICKET, new MessageRef("MSG32", Map.of("created", 1, "skipped", 0))));

        // the entry is a plain form field: FormData.append gives it no content type
        mvc.perform(multipart("/api/v1/imports/manual").param("entry", ENTRY).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value(TICKET.toString()))
                .andExpect(jsonPath("$.message.code").value("MSG32"))
                .andExpect(jsonPath("$.message.params.created").value(1));

        // the writer is the signed-in user, never a value of the request (D14)
        ArgumentCaptor<TSolveUser> user = ArgumentCaptor.forClass(TSolveUser.class);
        verify(manualEntries).save(user.capture(), eq(ENTRY), eq(List.of()));
        org.assertj.core.api.Assertions.assertThat(user.getValue().id()).isEqualTo(UUID.fromString(PM_ID));
        org.assertj.core.api.Assertions.assertThat(user.getValue().fullName()).isEqualTo("Nguyen Van An");
    }

    @Test
    @WithTSolveUser(roles = Role.TICKET_RESOLVER)
    void saveNeedsTheProjectManagerRole_AC_006_9() throws Exception {
        mvc.perform(multipart("/api/v1/imports/manual").param("entry", ENTRY).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        verifyNoInteractions(manualEntries);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void aRejectedEntryIs400WithItsMessageCodeAndFields_AC_006_3() throws Exception {
        when(manualEntries.save(any(), any(), any()))
                .thenThrow(ManualEntryRejectedException.missing(List.of("ticketId", "title")));

        mvc.perform(multipart("/api/v1/imports/manual").param("entry", ENTRY).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.params.field_name").value("ticketId, title"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ticketId"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void aTakenTicketIdIs409Msg31_AC_006_8() throws Exception {
        when(manualEntries.save(any(), any(), any())).thenThrow(ManualEntryRejectedException.ticketIdTaken("T-1"));

        mvc.perform(multipart("/api/v1/imports/manual").param("entry", ENTRY).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG31"))
                .andExpect(jsonPath("$.params.ticket_id").value("T-1"));
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void aRequestWithoutTheEntryPartIs400_AC_006_3() throws Exception {
        mvc.perform(multipart("/api/v1/imports/manual").with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(manualEntries);
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    @SuppressWarnings("unchecked")
    void theFilesPartsReachTheServiceInOrder_AC_006_2() throws Exception {
        when(manualEntries.save(any(), eq(ENTRY), any()))
                .thenReturn(new ManualEntryResponse(TICKET, new MessageRef("MSG32", Map.of())));

        mvc.perform(multipart("/api/v1/imports/manual")
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1}))
                        .file(new MockMultipartFile("files", "b.log", "text/plain", new byte[] {2, 3}))
                        .param("entry", ENTRY)
                        .with(csrf()))
                .andExpect(status().isCreated());

        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(manualEntries).save(any(), eq(ENTRY), files.capture());
        org.assertj.core.api.Assertions.assertThat(files.getValue())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("a.png", "b.log");
    }

    @Test
    @WithTSolveUser(roles = Role.PROJECT_MANAGER)
    void aRejectedFileIs400Msg34OnTheFilesField_AC_006_6() throws Exception {
        when(manualEntries.save(any(), any(), any())).thenThrow(ManualEntryRejectedException.fileRejected("x.zip"));

        mvc.perform(multipart("/api/v1/imports/manual")
                        .file(new MockMultipartFile("files", "x.zip", "application/zip", new byte[] {1}))
                        .param("entry", ENTRY)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG34"))
                .andExpect(jsonPath("$.params.file_name").value("x.zip"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("files"));
    }
}
