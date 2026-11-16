package vn.tsolve.workspace.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.tsolve.workspace.exception.JiraAppSiteNotRegisteredException;
import vn.tsolve.workspace.exception.JiraAppTokenRejectedException;
import vn.tsolve.workspace.service.JiraAppTokenService;

@WebMvcTest(JiraAppTokenController.class)
@Import({JiraAppSecurityConfig.class, SecurityConfig.class})
class JiraAppTokenControllerTest {
    private static final String URL = "/api/v1/jira-app/app-token";
    private static final String CLOUD = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final JiraAppPrincipal APP =
            new JiraAppPrincipal(CLOUD, null, "https://api.atlassian.com/ex/jira/" + CLOUD);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraAppTokenService tokens;

    @MockBean
    private JwtDecoder fitJwtDecoder;

    private static JiraAppAuthentication fit() {
        return new JiraAppAuthentication(APP, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }

    @Test
    void holdsTheTokenOfAScheduledTriggerWithoutAUser_AC_012_1() throws Exception {
        mvc.perform(post(URL).with(authentication(fit())).header("x-forge-oauth-system", "app-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(tokens).receive(CLOUD, APP.apiBaseUrl(), "app-token");
    }

    @Test
    void answers403Msg08ForASiteWithoutAWorkspace_AC_012_2() throws Exception {
        doThrow(new JiraAppSiteNotRegisteredException()).when(tokens).receive(CLOUD, APP.apiBaseUrl(), "app-token");
        mvc.perform(post(URL).with(authentication(fit())).header("x-forge-oauth-system", "app-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void answers400Msg06WithoutAToken_AC_012_3() throws Exception {
        doThrow(new JiraAppTokenRejectedException()).when(tokens).receive(CLOUD, APP.apiBaseUrl(), null);
        mvc.perform(post(URL).with(authentication(fit())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    void refusesACallWithoutAFit_AC_012_8() throws Exception {
        mvc.perform(post(URL).header("x-forge-oauth-system", "app-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(tokens);
    }
}
