package vn.tsolve.tracker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.tracker.dto.IssueCreatedPayload;
import vn.tsolve.tracker.service.IssueCreatedService;

@WebMvcTest(JiraAppEventController.class)
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAppEventControllerTest {

    private static final String URL = "/api/v1/jira-app/events/issue-created";
    private static final String BODY =
            """
            {"issue": {"id": "10001", "key": "ITSUP-1",
                       "fields": {"summary": "ignored", "project": {"id": "10000", "key": "ITSUP"}}},
             "timestamp": "1730000000000", "atlassianId": "ignored"}
            """;
    private static final JiraAppPrincipal SITE =
            new JiraAppPrincipal("cloud-1", null, "https://api.atlassian.com/ex/jira/cloud-1");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IssueCreatedService issueCreatedService;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @Test
    void acceptsCreatedEventWithoutSystemToken_AC_003_1() throws Exception {
        mvc.perform(fromSite().content(BODY)).andExpect(status().isAccepted());

        ArgumentCaptor<IssueCreatedPayload> payload = ArgumentCaptor.forClass(IssueCreatedPayload.class);
        verify(issueCreatedService).receive(payload.capture());
        assertThat(payload.getValue().issueKey()).isEqualTo("ITSUP-1");
        assertThat(payload.getValue().projectKey()).isEqualTo("ITSUP");
    }

    @Test
    void passesTheSummaryAdfDescriptionAndLabelsOfTheEventOn_AC_011_1() throws Exception {
        String body =
                """
                {"issue": {"key": "ITSUP-2", "fields": {
                   "project": {"key": "ITSUP"}, "summary": "VPN drops every hour",
                   "description": {"type": "doc", "version": 1, "content": [
                     {"type": "paragraph", "content": [{"type": "text", "text": "Reconnect fails."}]}]},
                   "labels": ["tsolve-chatbot"]}}}
                """;

        mvc.perform(fromSite().content(body)).andExpect(status().isAccepted());

        ArgumentCaptor<IssueCreatedPayload> payload = ArgumentCaptor.forClass(IssueCreatedPayload.class);
        verify(issueCreatedService).receive(payload.capture());
        assertThat(payload.getValue().summary()).isEqualTo("VPN drops every hour");
        assertThat(payload.getValue().description().path("type").asText()).isEqualTo("doc");
        assertThat(payload.getValue().issue().fields().labels()).containsExactly("tsolve-chatbot");
    }

    @Test
    void rejectsBodyWithoutProjectKey_AC_003_6() throws Exception {
        mvc.perform(fromSite().content("{\"issue\": {\"key\": \"ITSUP-1\", \"fields\": {\"summary\": \"x\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        verifyNoInteractions(issueCreatedService);
    }

    @Test
    void rejectsBodyWithoutIssueKey_AC_003_6() throws Exception {
        mvc.perform(fromSite().content("{\"issue\": {\"fields\": {\"project\": {\"key\": \"ITSUP\"}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"));
        verifyNoInteractions(issueCreatedService);
    }

    @Test
    void rejectsIssueKeyLongerThanItsColumn_AC_003_6() throws Exception {
        String key = "A".repeat(48) + "-12";
        mvc.perform(fromSite()
                        .content(
                                "{\"issue\": {\"key\": \"" + key + "\", \"fields\": {\"project\": {\"key\": \"A\"}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"));
        verifyNoInteractions(issueCreatedService);
    }

    @Test
    void rejectsBodyThatIsNotJson_AC_003_6() throws Exception {
        mvc.perform(fromSite().content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
        verifyNoInteractions(issueCreatedService);
    }

    @Test
    void rejectsCallWithoutFit_AC_001_2() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(issueCreatedService);
    }

    private static MockHttpServletRequestBuilder fromSite() {
        return post(URL).with(authentication(siteAuthentication())).contentType(MediaType.APPLICATION_JSON);
    }

    private static JiraAppAuthentication siteAuthentication() {
        return new JiraAppAuthentication(SITE, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }
}
