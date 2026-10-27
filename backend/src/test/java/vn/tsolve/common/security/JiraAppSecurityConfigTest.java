package vn.tsolve.common.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The FIT chain end to end, with a JWKS of local test keys standing in for Atlassian's. */
@WebMvcTest(controllers = JiraAppSecurityConfigTest.ProbeController.class)
@Import({
    JiraAppSecurityConfig.class,
    DefaultSecurityConfig.class,
    JiraAppSecurityConfigTest.TestDecoder.class,
    JiraAppSecurityConfigTest.ProbeController.class
})
class JiraAppSecurityConfigTest {

    private static final String URL = "/api/v1/jira-app/probe";
    private static final long WORKSPACE_ID = 7L;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JiraSiteLookup siteLookup;

    @Test
    void passesValidFitWithWorkspaceToHandler_AC_001_1() throws Exception {
        givenRegisteredSite();
        mvc.perform(post(URL).header("Authorization", "Bearer " + FitTokens.sign(b -> b)))
                .andExpect(status().isOk())
                .andExpect(content().string(FitTokens.CLOUD_ID + "/" + WORKSPACE_ID));
    }

    @Test
    void rejectsMissingAuthorization_AC_001_2() throws Exception {
        mvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void rejectsNonBearerAuthorization_AC_001_2() throws Exception {
        mvc.perform(post(URL).header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void rejectsFitSignedByUnknownKey_AC_001_3() throws Exception {
        givenRegisteredSite();
        mvc.perform(post(URL).header("Authorization", "Bearer " + FitTokens.sign(FitTokens.OTHER_KEY, b -> b)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void rejectsHs256SignedWithPublicKey_AC_001_3() throws Exception {
        givenRegisteredSite();
        mvc.perform(post(URL).header("Authorization", "Bearer " + FitTokens.signHs256WithPublicKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsFitForAnotherApp_AC_001_4() throws Exception {
        givenRegisteredSite();
        String fit = FitTokens.sign(b -> b.audience(List.of("ari:cloud:ecosystem::app/other")));
        mvc.perform(post(URL).header("Authorization", "Bearer " + fit))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void rejectsExpiredFit_AC_001_5() throws Exception {
        givenRegisteredSite();
        Instant past = Instant.now().minusSeconds(600);
        String fit = FitTokens.sign(b -> b.issueTime(Date.from(past.minusSeconds(60)))
                .notBeforeTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past)));
        mvc.perform(post(URL).header("Authorization", "Bearer " + fit)).andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsFitFromUnregisteredSite_AC_001_6() throws Exception {
        given(siteLookup.findWorkspaceId(anyString())).willReturn(Optional.empty());
        mvc.perform(post(URL).header("Authorization", "Bearer " + FitTokens.sign(b -> b)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void keepsTokenOutOfErrorResponse_AC_001_7() throws Exception {
        String fit = FitTokens.sign(b -> b.audience(List.of("ari:cloud:ecosystem::app/other")));
        mvc.perform(post(URL).header("Authorization", "Bearer " + fit))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(fit))))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this page."));
    }

    @Test
    void answers503WhenJwksUnavailable_AC_001_8() throws Exception {
        mvc.perform(post(URL).header("Authorization", "Bearer " + TestDecoder.JWKS_DOWN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    private void givenRegisteredSite() {
        given(siteLookup.findWorkspaceId(FitTokens.CLOUD_ID)).willReturn(Optional.of(WORKSPACE_ID));
    }

    @RestController
    static class ProbeController {

        @PreAuthorize("hasRole('" + Roles.JIRA_APP + "')")
        @PostMapping(URL)
        String probe(@AuthenticationPrincipal JiraAppPrincipal principal) {
            return principal.cloudId() + "/" + principal.workspaceId();
        }
    }

    /** The production decoder's settings, with the local test key in place of Atlassian's JWKS. */
    @TestConfiguration
    static class TestDecoder {

        static final String JWKS_DOWN = "jwks-down";

        @Bean
        JwtDecoder fitJwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(FitTokens.KEY.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(FitValidators.forApp(FitTokens.APP_ID));
            return token -> {
                if (JWKS_DOWN.equals(token)) {
                    throw new JwtException("Couldn't retrieve remote JWK set");
                }
                return decoder.decode(token);
            };
        }
    }
}
