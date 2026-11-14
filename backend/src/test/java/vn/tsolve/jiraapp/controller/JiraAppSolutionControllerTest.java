package vn.tsolve.jiraapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.jiraapp.dto.IssuePanelResponse;
import vn.tsolve.jiraapp.dto.SubmitSolutionRequest;
import vn.tsolve.jiraapp.dto.SubmittedTicketResponse;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraAppSolutionService;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.SolutionSectionRequiredException;
import vn.tsolve.ticket.exception.TicketAlreadyExistsException;

@WebMvcTest({JiraAppSolutionController.class, JiraAppPanelController.class})
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAppSolutionControllerTest {

    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);
    private static final UUID TICKET = UUID.fromString("70000000-0000-0000-0000-000000000126");
    private static final String SECTIONS =
            "{\"symptoms\":\"Drops\",\"rootCause\":\"Old\",\"resolutionSteps\":\"Update\",\"workaround\":\"\","
                    + "\"scopeOfApplicability\":\"Laptops\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraAppSolutionService solutions;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @Test
    void createsTheTicketAndAnswers201_AC_004_1() throws Exception {
        when(solutions.submit(eq(CALLER), eq("ITSUP-12"), any()))
                .thenReturn(new SubmittedTicketResponse(TICKET, "ITSUP-12", TicketStatus.PENDING_REVIEW));

        mvc.perform(post("/api/v1/jira-app/issues/ITSUP-12/solution")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"VPN drops\",\"sections\":" + SECTIONS + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value("ITSUP-12"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        verify(solutions)
                .submit(
                        CALLER,
                        "ITSUP-12",
                        new SubmitSolutionRequest(
                                "VPN drops", new SolutionSections("Drops", "Old", "Update", "", "Laptops")));
    }

    @Test
    void answersMsg01ForAnEmptyRequiredSection_AC_002_5() throws Exception {
        when(solutions.submit(eq(CALLER), eq("ITSUP-12"), any()))
                .thenThrow(new SolutionSectionRequiredException("Root cause", "rootCause"));

        mvc.perform(post("/api/v1/jira-app/issues/ITSUP-12/solution")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"VPN drops\",\"sections\":" + SECTIONS + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
    }

    @Test
    void answers409WhenTheIssueAlreadyHasATicket_AC_004_3() throws Exception {
        when(solutions.submit(eq(CALLER), eq("ITSUP-12"), any()))
                .thenThrow(new TicketAlreadyExistsException("ITSUP-12"));

        mvc.perform(post("/api/v1/jira-app/issues/ITSUP-12/solution")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"VPN drops\",\"sections\":" + SECTIONS + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG31"));
    }

    @Test
    void answers403Msg08ForANonMember_AC_001_2() throws Exception {
        when(solutions.submit(eq(CALLER), eq("ITSUP-12"), any())).thenThrow(new JiraIssueAccessDeniedException());

        mvc.perform(post("/api/v1/jira-app/issues/ITSUP-12/solution")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"VPN drops\",\"sections\":" + SECTIONS + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void refusesAMalformedIssueKey() throws Exception {
        mvc.perform(put("/api/v1/jira-app/issues/itsup-0/solution-draft")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SECTIONS))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(solutions);
    }

    @Test
    void refusesACallWithoutFit() throws Exception {
        mvc.perform(get("/api/v1/jira-app/issues/ITSUP-12/panel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(solutions);
    }

    @Test
    void savesTheDraftAndAnswers204_AC_005_1() throws Exception {
        mvc.perform(put("/api/v1/jira-app/issues/ITSUP-12/solution-draft")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SECTIONS))
                .andExpect(status().isNoContent());
        verify(solutions).saveDraft(CALLER, "ITSUP-12", new SolutionSections("Drops", "Old", "Update", "", "Laptops"));
    }

    @Test
    void answersMsg68WhenTheRewriteFails_AC_007_4() throws Exception {
        when(solutions.rewrite(eq(CALLER), any())).thenThrow(new AiRewriteUnavailableException());

        mvc.perform(post("/api/v1/jira-app/solution-rewrites")
                        .with(authentication(fit()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SECTIONS))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG68"));
    }

    @Test
    void showsThePanelState_AC_008_2() throws Exception {
        when(solutions.panel(CALLER, "ITSUP-12"))
                .thenReturn(new IssuePanelResponse(true, TicketStatus.PENDING_REVIEW, false, false));

        mvc.perform(get("/api/v1/jira-app/issues/ITSUP-12/panel").with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.ticketStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.canSearch").doesNotExist());
    }

    private static JiraAppAuthentication fit() {
        return new JiraAppAuthentication(CALLER, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }
}
