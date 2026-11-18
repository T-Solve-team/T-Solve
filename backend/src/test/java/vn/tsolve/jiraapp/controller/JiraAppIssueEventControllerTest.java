package vn.tsolve.jiraapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload;
import vn.tsolve.jiraapp.service.IssueUpdatedService;

@WebMvcTest(JiraAppIssueEventController.class)
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAppIssueEventControllerTest {

    private static final String URL = "/api/v1/jira-app/events/issue-updated";
    private static final String BODY = """
            {"atlassianId": "jira-mover", "cloudId": "payload-cloud",
             "issue": {"key": "ITSUP-7", "fields": {"summary": "VPN drops",
               "status": {"id": "10002", "statusCategory": {"key": "done"}},
               "assignee": {"accountId": "jira-assignee"}}},
             "changelog": {"items": [{"field": "status", "from": "3", "to": "10002"}]}}
            """;
    private static final JiraAppPrincipal SITE =
            new JiraAppPrincipal("cloud-1", null, "https://api.atlassian.com/ex/jira/cloud-1");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IssueUpdatedService issueUpdatedService;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @Test
    void acceptsTheEventFromTheJiraAppWithTheSiteOfTheVerifiedCall_AC_005_6() throws Exception {
        mvc.perform(post(URL)
                        .with(authentication(asRole(Roles.JIRA_APP)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        ArgumentCaptor<IssueUpdatedPayload> event = ArgumentCaptor.forClass(IssueUpdatedPayload.class);
        verify(issueUpdatedService).receive(eq(SITE), event.capture());
        assertThat(event.getValue().issueKey()).contains("ITSUP-7");
        assertThat(event.getValue().statusCategory()).contains("done");
        assertThat(event.getValue().draftOwner()).contains("jira-assignee");
        assertThat(event.getValue().statusChange()).isPresent();
    }

    @Test
    void acceptsAnEventItCannotReadAndLeavesItToTheService_AC_005_10() throws Exception {
        mvc.perform(post(URL)
                        .with(authentication(asRole(Roles.JIRA_APP)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issue\": {}}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void refusesACallWithoutAFit_AC_005_6() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(issueUpdatedService);
    }

    private static JiraAppAuthentication asRole(String role) {
        return new JiraAppAuthentication(SITE, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
