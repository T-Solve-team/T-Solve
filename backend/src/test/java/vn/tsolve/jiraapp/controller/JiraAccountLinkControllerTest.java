package vn.tsolve.jiraapp.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.security.JiraAppAuthentication;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.Roles;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStart;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus;
import vn.tsolve.jiraapp.dto.JiraAccountLinkStatus.Status;
import vn.tsolve.jiraapp.exception.JiraAccountLinkUnavailableException;
import vn.tsolve.jiraapp.exception.JiraSiteNotConnectedException;
import vn.tsolve.jiraapp.service.JiraAccountLinkPageService;

@WebMvcTest(JiraAccountLinkController.class)
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAccountLinkControllerTest {

    private static final String LINK = "/api/v1/jira-app/account-link";
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraAccountLinkPageService links;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    @Test
    void answersTheLinkedAccount_AC_014_2() throws Exception {
        when(links.status(CALLER))
                .thenReturn(JiraAccountLinkStatus.linked(
                        "An Nguyen", "an@acme.example", Instant.parse("2026-11-17T08:00:00Z")));

        mvc.perform(get(LINK).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINKED"))
                .andExpect(jsonPath("$.fullName").value("An Nguyen"))
                .andExpect(jsonPath("$.email").value("an@acme.example"))
                .andExpect(jsonPath("$.linkedAt").value("2026-11-17T08:00:00Z"));
    }

    @Test
    void answersNotLinkedWithoutAccountFields_AC_014_2() throws Exception {
        when(links.status(CALLER)).thenReturn(JiraAccountLinkStatus.of(Status.NOT_LINKED));

        mvc.perform(get(LINK).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_LINKED"))
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void answersTheLinkToOpen_AC_014_6() throws Exception {
        when(links.start(CALLER)).thenReturn(new JiraAccountLinkStart("https://acme.tsolve.io.vn/jira-link?request=x"));

        mvc.perform(post(LINK).with(authentication(fit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://acme.tsolve.io.vn/jira-link?request=x"));
    }

    @Test
    void mapsASiteOfNoWorkspaceTo403Msg124_AC_014_9() throws Exception {
        when(links.start(CALLER)).thenThrow(new JiraSiteNotConnectedException());

        mvc.perform(post(LINK).with(authentication(fit())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG124"));
    }

    @Test
    void mapsAnUnavailableLinkTo503Msg06_AC_014_9() throws Exception {
        when(links.start(CALLER)).thenThrow(new JiraAccountLinkUnavailableException());

        mvc.perform(post(LINK).with(authentication(fit())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void refusesACallWithoutAFit() throws Exception {
        mvc.perform(get(LINK)).andExpect(status().isUnauthorized());
        mvc.perform(post(LINK)).andExpect(status().isUnauthorized());
        verifyNoInteractions(links);
    }

    private static JiraAppAuthentication fit() {
        return new JiraAppAuthentication(CALLER, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }
}
