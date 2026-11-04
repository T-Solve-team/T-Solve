package vn.tsolve.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.config.WebConfig;

@WebMvcTest(SecurityConfigTest.Probe.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, WebConfig.class, SecurityConfigTest.Probe.class})
@TestPropertySource(properties = {"server.port=0", "CORS_ALLOWED_ORIGINS=https://client.example.test"})
class SecurityConfigTest {
    @Autowired
    private MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping({
            "/api/probe",
            "/outside",
            "/v3/api-docs.yaml",
            "/v3/api-docs",
            "/v3/api-docs/probe",
            "/swagger-ui.html",
            "/swagger-ui/probe"
        })
        String read() {
            return "ok";
        }

        @GetMapping("/api/admin")
        @PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_MANAGER')")
        String admin() {
            return "ok";
        }

        @RequestMapping(
                value = "/api/probe",
                method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        String write() {
            return "ok";
        }
    }

    @Test
    void anonymousIsDeniedEvenWithoutAnnotation_AC_002_4_AC_002_6() throws Exception {
        mvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(post("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
    }

    @Test
    @WithTSolveUser(roles = {Role.TICKET_RESOLVER, Role.KNOWLEDGE_MANAGER})
    void roleUnionAllowsEitherRole_AC_002_3_AC_002_12() throws Exception {
        mvc.perform(get("/api/admin")).andExpect(status().isOk());
    }

    @Test
    @WithTSolveUser
    void rejectsInsufficientRoleAndOutsidePaths_AC_002_5_AC_002_7() throws Exception {
        mvc.perform(get("/api/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        mvc.perform(get("/outside"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @Test
    void docsArePublicAndNoBasicChallenge_AC_002_8_AC_002_10() throws Exception {
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/api/probe").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/v3/api-docs",
                "/v3/api-docs/probe",
                "/v3/api-docs.yaml",
                "/swagger-ui.html",
                "/swagger-ui/probe"
            })
    void allowsPublicPaths_AC_002_8(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void permitsConfiguredPreflight_AC_002_9() throws Exception {
        mvc.perform(options("/api/probe")
                        .header("Origin", "https://client.example.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/probe")
                        .header("Origin", "https://untrusted.example.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithTSolveUser
    void enforcesCsrfAndSetsReadableCookie_AC_002_11_AC_002_13() throws Exception {
        var token = mvc.perform(get("/api/probe"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/probe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(post("/api/probe").cookie(token).header("X-XSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(post("/api/probe").cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnonymousOutsideAndValidCsrfMutation_AC_002_4_AC_002_7() throws Exception {
        mvc.perform(get("/outside"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(
                        post("/api/probe")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @WithTSolveUser
    void enforcesCsrfOnEveryMutation_AC_002_11_AC_002_13(String method) throws Exception {
        var token = mvc.perform(get("/api/probe")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(request(HttpMethod.valueOf(method), "/api/probe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(request(HttpMethod.valueOf(method), "/api/probe")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG07"));
        mvc.perform(request(HttpMethod.valueOf(method), "/api/probe")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk());
    }
}
