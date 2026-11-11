package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * AC-001.75: a signed-in request runs with its session's Workspace bound for the whole request, and nothing stays bound
 * afterwards. MockMvc runs on the calling thread, so the test thread stands in for a pooled Tomcat thread.
 */
@Import({WorkspaceProbeTestController.class, EscapingFailureTestFilter.class})
class WorkspaceBindingTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String PROBE = "/api/v1/test/workspace";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @SpyBean
    private TenantScope scope;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void cleanTenants() {
        WorkspaceProbeTestController.reset();
        EscapingFailureTestFilter.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    private static JdbcTemplate dbOf(WorkspaceRef workspace) {
        return workspace.id().equals(ACME.id()) ? TwoTenantDatabases.tenantA() : TwoTenantDatabases.tenantB();
    }

    private UUID seed(WorkspaceRef workspace, UUID id, String email) {
        dbOf(workspace)
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at)"
                                + " VALUES (?, ?, 'Someone', 'ACTIVE', now())",
                        id,
                        email);
        return id;
    }

    private String sessionOf(WorkspaceRef workspace, UUID account) {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                workspace.id(),
                account,
                null,
                workspace.siteHost(),
                "id-token-hint",
                clock.instant(),
                clock.instant()));
    }

    private MockHttpServletResponse probe(String host, String path, String cookie) throws Exception {
        var request = get(url(host, PROBE + path));
        if (cookie != null) {
            request.cookie(new Cookie(SESSION_COOKIE, cookie));
        }
        return perform(request);
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private void assertNothingBoundOnThisThread() {
        assertThatThrownBy(() -> scope.currentWorkspaceId()).isInstanceOf(IllegalStateException.class);
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.75 a session of Workspace A reads only A, and B only B")
    void aSessionReadsOnlyItsWorkspace_AC_001_75() throws Exception {
        UUID shared = UUID.randomUUID();
        seed(ACME, shared, "same@acme.example");
        seed(BETA, shared, "same@beta.example");
        seed(BETA, UUID.randomUUID(), "other@beta.example");
        String acme = sessionOf(ACME, shared);
        String beta = sessionOf(BETA, shared);

        MockHttpServletResponse onAcme = probe(ACME.siteHost(), "/bound", acme);
        MockHttpServletResponse onBeta = probe(BETA.siteHost(), "/bound", beta);

        assertThat(onAcme.getStatus()).isEqualTo(200);
        assertThat(body(onAcme).path("workspaceId").asText())
                .isEqualTo(ACME.id().toString());
        assertThat(texts(body(onAcme).path("emails"))).containsExactly("same@acme.example");
        assertThat(onBeta.getStatus()).isEqualTo(200);
        assertThat(body(onBeta).path("workspaceId").asText())
                .isEqualTo(BETA.id().toString());
        assertThat(texts(body(onBeta).path("emails"))).containsExactly("other@beta.example", "same@beta.example");
    }

    @Test
    @DisplayName("AC-001.75 the binding is gone after the request, also when the endpoint throws")
    void noBindingAfterTheRequestEvenWhenItThrows_AC_001_75() throws Exception {
        UUID acmeAccount = seed(ACME, UUID.randomUUID(), "a@acme.example");
        UUID betaAccount = seed(BETA, UUID.randomUUID(), "b@beta.example");
        String acme = sessionOf(ACME, acmeAccount);
        String beta = sessionOf(BETA, betaAccount);

        MockHttpServletResponse failed = probe(ACME.siteHost(), "/throw", acme);

        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(WorkspaceProbeTestController.boundWhenThrown()).isEqualTo(ACME.id());
        assertNothingBoundOnThisThread();

        MockHttpServletResponse next = probe(BETA.siteHost(), "/bound", beta);
        assertThat(next.getStatus()).isEqualTo(200);
        assertThat(body(next).path("workspaceId").asText()).isEqualTo(BETA.id().toString());
    }

    @Test
    @DisplayName("edge 134 a failure that leaves the filter chain is rethrown unchanged and leaves no binding")
    void escapingFailureIsRethrownAndUnbinds_edge134() throws Exception {
        String acme = sessionOf(ACME, seed(ACME, UUID.randomUUID(), "a@acme.example"));

        Throwable thrown =
                org.assertj.core.api.Assertions.catchThrowable(() -> probe(ACME.siteHost(), "/escape", acme));

        assertThat(thrown).isNotNull();
        ServletException escaped = EscapingFailureTestFilter.thrown();
        assertThat(escaped).isNotNull();
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = thrown; t != null && !chain.contains(t); t = t.getCause()) {
            chain.add(t);
        }
        assertThat(chain).as("cause chain").anyMatch(t -> t == escaped);
        assertThat(EscapingFailureTestFilter.boundWhenThrown()).isEqualTo(ACME.id());
        assertNothingBoundOnThisThread();
    }

    static Stream<Arguments> unboundCases() {
        return Stream.of(
                Arguments.of("no cookie on acme", "acme.localhost", "none", "MSG07"),
                Arguments.of("acme session on api.", "api.localhost", "acme", "MSG07"),
                Arguments.of("acme session on login.", "login.localhost", "acme", "MSG07"),
                Arguments.of("operator session on acme", "acme.localhost", "operator", "MSG123"),
                Arguments.of("operator session on admin.", "admin.localhost", "operator", "MSG123"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unboundCases")
    @DisplayName("AC-001.75 no binding for an anonymous request, a session on api. or login., or an Operator session")
    void noBindingWithoutAWorkspaceSession_AC_001_75(String label, String host, String session, String code)
            throws Exception {
        String cookie = null;
        if ("acme".equals(session)) {
            cookie = sessionOf(ACME, seed(ACME, UUID.randomUUID(), "a@acme.example"));
        } else if ("operator".equals(session)) {
            cookie = store.create(new WebSession(
                    WebSession.Kind.OPERATOR,
                    null,
                    null,
                    "ops@example.test",
                    host,
                    "id-token-hint",
                    clock.instant(),
                    clock.instant()));
        }

        MockHttpServletResponse response = probe(host, "/bound", cookie);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response).path("code").asText()).isEqualTo(code);
        assertThat(WorkspaceProbeTestController.calls()).isZero();
        verify(scope, never()).runIn(any(), any());
        assertNothingBoundOnThisThread();
    }

    @Test
    @DisplayName("AC-001.75 a signed-in request that needs the Workspace with none bound answers 500 MSG06, never"
            + " another Workspace's data")
    void noWorkspaceBoundAnswers500_AC_001_75() throws Exception {
        String acme = sessionOf(ACME, seed(ACME, UUID.randomUUID(), "a@acme.example"));
        seed(BETA, UUID.randomUUID(), "b@beta.example");

        MvcResult started = mvc.perform(
                        get(url(ACME.siteHost(), PROBE + "/callable")).cookie(new Cookie(SESSION_COOKIE, acme)))
                .andExpect(request().asyncStarted())
                .andReturn();
        MockHttpServletResponse response =
                mvc.perform(asyncDispatch(started)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body(response).path("code").asText()).isEqualTo("MSG06");
        assertThat(response.getContentAsString())
                .doesNotContain("acme.example")
                .doesNotContain("beta.example")
                .doesNotContain("workspaceId");
    }
}
