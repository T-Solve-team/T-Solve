package vn.tsolve.tracker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.DefaultSecurityConfig;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.JiraSiteLookup;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.WorkspaceContext;
import vn.tsolve.tracker.dto.IssueEventPayload;
import vn.tsolve.tracker.entity.IssueEventType;
import vn.tsolve.tracker.service.IssueEventService;

@WebMvcTest(JiraAppEventController.class)
@Import({JiraAppSecurityConfig.class, DefaultSecurityConfig.class})
class JiraAppEventControllerTest {

    private static final String URL = "/api/v1/jira-app/events/issue-created";
    private static final String BODY =
            """
            {"issue": {"id": "10001", "key": "ITSUP-1", "fields": {"summary": "ignored"}},
             "timestamp": "1730000000000", "atlassianId": "ignored"}
            """;
    private static final JiraAppPrincipal SITE =
            new JiraAppPrincipal("cloud-1", 7L, null, "https://api.atlassian.com/ex/jira/cloud-1");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IssueEventService issueEventService;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @MockBean
    private JiraSiteLookup siteLookup;

    @Test
    void acceptsCreatedEvent_AC_003_1() throws Exception {
        mvc.perform(fromSite().content(BODY)).andExpect(status().isAccepted());

        verify(issueEventService)
                .receive(eq("cloud-1"), eq(IssueEventType.ISSUE_CREATED), any(IssueEventPayload.class), eq("token"));
    }

    @Test
    void recordsUnderTheSitesWorkspace_AC_003_7() throws Exception {
        AtomicLong workspaceDuringReceive = new AtomicLong();
        willAnswer(call -> {
                    workspaceDuringReceive.set(WorkspaceContext.current());
                    return null;
                })
                .given(issueEventService)
                .receive(any(), any(), any(), any());

        mvc.perform(fromSite().content(BODY)).andExpect(status().isAccepted());

        assertThat(workspaceDuringReceive.get()).isEqualTo(7L);
        assertThat(WorkspaceContext.current()).isEqualTo(WorkspaceContext.NONE);
    }

    @Test
    void rejectsEventWithoutSystemToken_AC_003_5() throws Exception {
        mvc.perform(post(URL)
                        .with(authentication(siteAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(issueEventService);
    }

    @Test
    void rejectsBodyWithoutIssueId_AC_003_6() throws Exception {
        mvc.perform(fromSite().content("{\"issue\": {\"key\": \"ITSUP-1\"}, \"timestamp\": \"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(issueEventService);
    }

    @Test
    void rejectsTimestampBeyondLong_AC_003_6() throws Exception {
        mvc.perform(
                        fromSite()
                                .content(
                                        "{\"issue\": {\"id\": \"1\", \"key\": \"A-1\"}, \"timestamp\": \"9999999999999999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(issueEventService);
    }

    @Test
    void rejectsBodyThatIsNotJson_AC_003_6() throws Exception {
        mvc.perform(fromSite().content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void rejectsCallWithoutFit_AC_001_2() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(issueEventService);
    }

    private static MockHttpServletRequestBuilder fromSite() {
        return post(URL)
                .with(authentication(siteAuthentication()))
                .header(JiraAppEventController.SYSTEM_TOKEN_HEADER, "token")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static JiraAppAuthentication siteAuthentication() {
        return new JiraAppAuthentication(SITE, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }
}
