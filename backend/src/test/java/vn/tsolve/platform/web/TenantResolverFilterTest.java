package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/**
 * Task 10a (KAN-258): the web Tenant Resolver, called directly. The holder is the real one; every test ends with
 * nothing bound, because its {@code ThreadLocal} is static.
 */
@ExtendWith(OutputCaptureExtension.class)
class TenantResolverFilterTest {

    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID GAMMA = UUID.fromString("00000000-0000-4000-8000-00000000000c");

    private static final String ACME = "acme.tsolve.io.vn";
    private static final String BETA = "beta.tsolve.io.vn";
    private static final String NOSUCH = "nosuch.tsolve.io.vn";
    private static final String LOGIN = "login.tsolve.io.vn";
    private static final String API = "api.tsolve.io.vn";
    private static final String ADMIN = "admin.tsolve.io.vn";
    private static final String BARE = "tsolve.io.vn";

    private static final Map<String, String> SRS_TEXT = Map.of(
            "MSG06", "Something went wrong. Please try again later.",
            "MSG150", "This workspace is suspended. Please contact your company administrator.",
            "MSG151", "This workspace does not exist. Check the address.");

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final AtomicInteger knownChecks = new AtomicInteger();
    private final ThreadLocalTenantContext holder = new ThreadLocalTenantContext(id -> {
        knownChecks.incrementAndGet();
        return A.equals(id) || B.equals(id);
    });
    private final FakeCatalog catalog = new FakeCatalog();

    TenantResolverFilterTest() {
        catalog.put(A, "acme", WorkspaceStatus.ACTIVE);
        catalog.put(B, "beta", WorkspaceStatus.ACTIVE);
    }

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName("AC-002.2 AC-002.3 AC-002.10 AC-002.13 edge 1 binds the Workspace of the Host header and nothing else")
    void bindsFromTheHostHeaderOnly_AC_002_2_AC_002_3_AC_002_10_AC_002_13() throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));

        RecordingChain plain = new RecordingChain();
        filter.doFilter(get(ACME, "/api/v1/me"), new MockHttpServletResponse(), plain);
        assertThat(plain.seen).containsExactly(Optional.of(A));
        assertThat(holder.currentWorkspaceId()).isEmpty();

        MockHttpServletRequest spoofed = request("POST", ACME, "/api/v1/me", "/api/v1/me");
        spoofed.addHeader("X-Forwarded-Host", BETA);
        spoofed.addHeader("Forwarded", "host=" + BETA);
        spoofed.addHeader("X-Workspace-Id", B.toString());
        spoofed.setParameter("workspaceId", B.toString());
        spoofed.setCookies(new Cookie("workspaceId", B.toString()));
        spoofed.setContentType("application/json");
        spoofed.setContent(("{\"workspaceId\":\"" + B + "\"}").getBytes(StandardCharsets.UTF_8));
        AtomicInteger ranInB = new AtomicInteger();
        AtomicInteger ranInA = new AtomicInteger();
        RecordingChain chain = new RecordingChain();
        chain.action = () -> {
            assertThatThrownBy(() -> holder.runIn(B, (Runnable) ranInB::incrementAndGet))
                    .isInstanceOf(RuntimeException.class);
            holder.runIn(A, (Runnable) ranInA::incrementAndGet);
        };

        filter.doFilter(spoofed, new MockHttpServletResponse(), chain);

        assertThat(chain.seen).containsExactly(Optional.of(A));
        assertThat(ranInB).as("the other Workspace's action never ran").hasValue(0);
        assertThat(ranInA).hasValue(1);
        assertThat(catalog.lookups("beta")).isZero();
        assertThat(holder.currentWorkspaceId()).isEmpty();

        int lookupsSoFar = catalog.totalLookups();
        for (MockHttpServletRequest unbound :
                List.of(get(LOGIN, "/api/v1/me"), get(ADMIN, "/api/v1/me"), get(API, "/api/v1/jira-app/x"))) {
            RecordingChain passed = new RecordingChain();
            filter.doFilter(unbound, new MockHttpServletResponse(), passed);
            assertThat(passed.seen)
                    .as(unbound.getHeader("Host") + unbound.getRequestURI())
                    .containsExactly(Optional.empty());
            assertThat(holder.currentWorkspaceId()).isEmpty();
        }
        assertThat(catalog.totalLookups()).isEqualTo(lookupsSoFar);
    }

    @Test
    @DisplayName("AC-002.11 edge 2 unbinds when the request ends, with an exception or after a re-dispatch")
    void unbindsWhenTheRequestEnds_AC_002_11() throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));

        RuntimeException unchecked = new RuntimeException("handler failed");
        RecordingChain throwsUnchecked = new RecordingChain();
        throwsUnchecked.action = () -> {
            throw unchecked;
        };
        assertThatThrownBy(
                        () -> filter.doFilter(get(ACME, "/api/v1/me"), new MockHttpServletResponse(), throwsUnchecked))
                .isSameAs(unchecked);
        assertThat(throwsUnchecked.seen).containsExactly(Optional.of(A));
        assertThat(holder.currentWorkspaceId()).isEmpty();

        ServletException checked = new ServletException("servlet failed");
        RecordingChain throwsChecked = new RecordingChain();
        throwsChecked.action = () -> {
            throw checked;
        };
        assertThatThrownBy(() -> filter.doFilter(get(ACME, "/api/v1/me"), new MockHttpServletResponse(), throwsChecked))
                .isSameAs(checked);
        assertThat(throwsChecked.seen).containsExactly(Optional.of(A));
        assertThat(holder.currentWorkspaceId()).isEmpty();

        RecordingChain next = new RecordingChain();
        filter.doFilter(get(BETA, "/api/v1/me"), new MockHttpServletResponse(), next);
        assertThat(next.seen).containsExactly(Optional.of(B));

        int acmeBefore = catalog.lookups("acme");
        MockHttpServletRequest request = get(ACME, "/api/v1/me");
        RecordingChain dispatched = new RecordingChain();
        filter.doFilter(request, new MockHttpServletResponse(), dispatched);
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, new MockHttpServletResponse(), dispatched);
        assertThat(dispatched.seen).containsExactly(Optional.of(A), Optional.of(A));
        assertThat(catalog.lookups("acme"))
                .as("no second lookup on the ASYNC re-dispatch")
                .isEqualTo(acmeBefore + 1);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        int lookupsBeforeError = catalog.totalLookups();
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/me");
        request.setRequestURI("/error");
        request.setServletPath("/error");
        RecordingChain error = new RecordingChain();
        filter.doFilter(request, new MockHttpServletResponse(), error);
        assertThat(error.seen).containsExactly(Optional.empty());
        assertThat(catalog.totalLookups()).isEqualTo(lookupsBeforeError);
    }

    @Test
    @DisplayName(
            "AC-002.4 AC-002.5 AC-002.7 AC-002.9 AC-013.11 edge 18 refuses unknown, bad-label and Suspended hosts before the chain")
    void refusesUnknownBadLabelAndSuspendedBeforeTheChain_AC_002_4_AC_002_5_AC_002_7_AC_002_9_AC_013_11()
            throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));
        String tooLong = "a".repeat(64);
        catalog.put(A, "acme", WorkspaceStatus.SUSPENDED);

        for (Call call : List.of(
                new Call("GET", "/api/v1/me", null),
                new Call("GET", "/api/v1/auth/callback", "code=abc&state=xyz"),
                new Call("POST", "/api/v1/auth/logout", null))) {
            assertRefused(filter, call.to(NOSUCH), 404, "MSG151");

            int lookupsBefore = catalog.totalLookups();
            for (String label : List.of("-acme", "a_b", tooLong)) {
                assertRefused(filter, call.to(label + ".tsolve.io.vn"), 404, "MSG151", label);
            }
            assertThat(catalog.totalLookups()).as("no lookup for a bad label").isEqualTo(lookupsBefore);

            MockHttpServletRequest withSession = call.to(ACME);
            withSession.setCookies(new Cookie("SESSION", "a-session-id"));
            withSession.addHeader("Authorization", "Bearer a-token");
            assertRefused(filter, withSession, 403, "MSG150");
        }
        assertThat(catalog.lookups("nosuch")).as("one lookup per request").isEqualTo(3);
        assertThat(catalog.lookups("acme")).as("one lookup per request").isEqualTo(3);

        assertRefused(filter, get("gamma.tsolve.io.vn", "/api/v1/me"), 404, "MSG151");
        catalog.put(GAMMA, "gamma", WorkspaceStatus.ACTIVE);
        RecordingChain created = new RecordingChain();
        filter.doFilter(get("gamma.tsolve.io.vn", "/api/v1/me"), new MockHttpServletResponse(), created);
        assertThat(created.seen).containsExactly(Optional.of(GAMMA));
        assertThat(catalog.lookups("gamma")).isEqualTo(2);

        catalog.put(A, "acme", WorkspaceStatus.ACTIVE);
        RecordingChain active = new RecordingChain();
        filter.doFilter(get(ACME, "/api/v1/me"), new MockHttpServletResponse(), active);
        assertThat(active.seen).containsExactly(Optional.of(A));
        catalog.put(A, "acme", WorkspaceStatus.SUSPENDED);
        assertRefused(filter, get(ACME, "/api/v1/me"), 403, "MSG150");
        assertThat(catalog.lookups("acme")).isEqualTo(5);
    }

    @Test
    @DisplayName(
            "AC-002.19 edge 20 edge 91 health and API docs pass on any host, and with no catalog every request passes")
    void passesHealthAndApiDocsAndEverythingWithoutACatalog_AC_002_19() throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));
        catalog.put(A, "acme", WorkspaceStatus.SUSPENDED);
        List<String> hosts = new ArrayList<>(List.of("localhost:8080", "acme.example.com", NOSUCH, ACME, API));
        hosts.add(null);

        for (String host : hosts) {
            for (String path : List.of(
                    "/actuator/health",
                    "/v3/api-docs",
                    "/v3/api-docs/x",
                    "/v3/api-docs.yaml",
                    "/swagger-ui.html",
                    "/swagger-ui/index.html")) {
                assertPassedUntouched(filter, get(host, path));
            }
        }
        assertThat(catalog.totalLookups()).isZero();

        for (MockHttpServletRequest closed : List.of(
                get("localhost:8080", "/actuator/env"),
                get("localhost:8080", "/actuator/healthx"),
                request("GET", "localhost:8080", "/actuator/health/%2e%2e/env", "/actuator/env"))) {
            assertRefused(filter, closed, 404, "MSG151");
        }

        TenantResolverFilter noCatalog =
                filter(new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class));
        for (MockHttpServletRequest any : List.of(
                get(NOSUCH, "/api/v1/me"),
                get(ACME, "/api/v1/me"),
                get("localhost:8080", "/api/v1/me"),
                get(ACME, "/api/v1/jira-app/x"),
                get(API, "/api/v1/me"),
                get(null, "/api/v1/me"))) {
            assertPassedUntouched(noCatalog, any);
        }
    }

    @Test
    @DisplayName("AC-002.9 edge 17 edge 22 answers MSG06 when the Registry is down and logs only the host and the code")
    void failsClosedWhenTheRegistryIsDownAndLogsHostAndCodeOnly_AC_002_9(CapturedOutput output) throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));
        catalog.failure = new DataAccessResourceFailureException(
                "could not connect to jdbc:postgresql://registry.internal:5432/tsolve_registry password=s3cr3t");

        for (String path : List.of("/api/v1/auth/callback", "/api/v1/me")) {
            String body = assertRefused(filter, get(ACME, path), 500, "MSG06");
            assertThat(body).doesNotContain("jdbc:", "registry.internal", "s3cr3t");
        }
        assertThat(output.getAll())
                .contains(ACME, "MSG06", "DataAccessResourceFailureException")
                .doesNotContain("jdbc:", "registry.internal", "s3cr3t");

        catalog.failure = null;
        catalog.put(A, "acme", WorkspaceStatus.SUSPENDED);
        for (String host : List.of(NOSUCH, "-acme.tsolve.io.vn", ACME)) {
            MockHttpServletRequest request = request("POST", host, "/api/v1/auth/callback", "/api/v1/auth/callback");
            request.addHeader("Authorization", "Bearer bearer-marker-7f3a");
            request.setCookies(new Cookie("SESSION", "session-marker-91bc"));
            request.setContentType("application/json");
            request.setContent("{\"note\":\"body-marker-55de\"}".getBytes(StandardCharsets.UTF_8));
            request.setQueryString("code=code-marker-c0de&state=xyz");
            request.setParameter("code", "code-marker-c0de");
            boolean suspended = ACME.equals(host);
            assertRefused(filter, request, suspended ? 403 : 404, suspended ? "MSG150" : "MSG151");
        }
        assertThat(output.getAll())
                .doesNotContain("bearer-marker-7f3a", "session-marker-91bc", "body-marker-55de", "code-marker-c0de");
    }

    private enum Outcome {
        MSG06,
        MSG150,
        MSG151,
        PASSES_UNBOUND,
        PASSES_IN_A
    }

    static Stream<Arguments> jiraAndApiPaths() {
        List<Arguments> cases = new ArrayList<>();
        for (String host : List.of(ACME, LOGIN, ADMIN)) {
            cases.add(arguments(host, "/api/v1/jira-app/x", "/api/v1/jira-app/x", Outcome.MSG06));
            cases.add(arguments(host, "/api/v1/jira-app", "/api/v1/jira-app", Outcome.MSG06));
            cases.add(arguments(host, "/api/v1/jira%2Dapp/x", "/api/v1/jira-app/x", Outcome.MSG06));
            cases.add(arguments(host, "/api/v1/%6Aira-app/x", "/api/v1/jira-app/x", Outcome.MSG06));
            cases.add(arguments(host, "/api/v1/jira-app/%6Cifecycle", "/api/v1/jira-app/lifecycle", Outcome.MSG06));
        }
        for (String path : List.of("/api/v1/me", "/", "/api/v1/auth/callback", "/api/v1/jira-appx")) {
            cases.add(arguments(API, path, path, Outcome.MSG06));
        }
        cases.add(arguments(API, "/api/v1/jira-app/x", "/api/v1/jira-app/x", Outcome.PASSES_UNBOUND));
        cases.add(arguments(API, "/api/v1/jira%2Dapp/x", "/api/v1/jira-app/x", Outcome.PASSES_UNBOUND));
        cases.add(arguments(API, "/actuator/health", "/actuator/health", Outcome.PASSES_UNBOUND));
        // A site host with a path that only looks like the Jira App's is an ordinary site request (AC-002.2).
        cases.add(arguments(ACME, "/api/v1/jira-appx", "/api/v1/jira-appx", Outcome.PASSES_IN_A));
        // The host is judged before the path. The MSG150 case runs with acme Suspended.
        cases.add(arguments(ACME, "/api/v1/jira-app/x", "/api/v1/jira-app/x", Outcome.MSG150));
        cases.add(arguments(NOSUCH, "/api/v1/jira-app/x", "/api/v1/jira-app/x", Outcome.MSG151));
        cases.add(arguments(BARE, "/api/v1/jira-app/x", "/api/v1/jira-app/x", Outcome.MSG151));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}{1} gives {3}")
    @MethodSource("jiraAndApiPaths")
    @DisplayName("AC-005.7 AC-005.8 edge 90 closes Jira App paths off the api host and every other path on it")
    void refusesJiraAppPathsOffTheApiHostAndOtherPathsOnIt_AC_005_7_AC_005_8(
            String host, String rawPath, String decodedPath, Outcome expected) throws Exception {
        TenantResolverFilter filter = filter(providerWith(catalog));
        if (expected == Outcome.MSG150) {
            catalog.put(A, "acme", WorkspaceStatus.SUSPENDED);
        }
        MockHttpServletRequest request = request("GET", host, rawPath, decodedPath);

        switch (expected) {
            case MSG06, MSG151 -> assertRefused(filter, request, 404, expected.name());
            case MSG150 -> assertRefused(filter, request, 403, expected.name());
            case PASSES_UNBOUND, PASSES_IN_A -> {
                RecordingChain chain = new RecordingChain();
                filter.doFilter(request, new MockHttpServletResponse(), chain);
                assertThat(chain.seen)
                        .containsExactly(expected == Outcome.PASSES_IN_A ? Optional.of(A) : Optional.empty());
            }
        }
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    private TenantResolverFilter filter(ObjectProvider<WorkspaceCatalog> provider) {
        return new TenantResolverFilter(
                HostClassifier.fromTemplate("https://{slug}.tsolve.io.vn"),
                provider,
                holder,
                new SecurityProblemWriter(mapper));
    }

    private static ObjectProvider<WorkspaceCatalog> providerWith(WorkspaceCatalog fake) {
        return new StaticListableBeanFactory(Map.of("catalog", fake)).getBeanProvider(WorkspaceCatalog.class);
    }

    /** A request as Tomcat hands it to a filter: the raw URI, and the decoded and normalized servlet path. */
    private static MockHttpServletRequest request(String method, String host, String rawUri, String decodedPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, rawUri);
        request.setServletPath(decodedPath);
        if (host != null) {
            request.addHeader("Host", host);
        }
        return request;
    }

    private static MockHttpServletRequest get(String host, String path) {
        return request("GET", host, path, path);
    }

    /** One refusal: the problem body with the SRS text, nothing about the Workspace, and no chain. */
    private String assertRefused(
            TenantResolverFilter filter, MockHttpServletRequest request, int status, String code, String... alsoAbsent)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        String what = request.getHeader("Host") + request.getRequestURI();
        assertThat(chain.seen).as("the chain was reached for " + what).isEmpty();
        assertThat(response.getStatus()).as(what).isEqualTo(status);
        assertThat(response.getContentType()).as(what).startsWith("application/problem+json");
        String body = response.getContentAsString();
        JsonNode problem = mapper.readTree(body);
        assertThat(problem.path("status").asInt()).as(what).isEqualTo(status);
        assertThat(problem.path("code").asText()).as(what).isEqualTo(code);
        assertThat(problem.path("detail").asText()).as(what).isEqualTo(SRS_TEXT.get(code));
        assertThat(body)
                .as(what)
                .doesNotContain("acme", "beta", "gamma", "nosuch", A.toString(), B.toString(), GAMMA.toString());
        for (String absent : alsoAbsent) {
            assertThat(body).as(what).doesNotContain(absent);
        }
        assertThat(holder.currentWorkspaceId()).as(what).isEmpty();
        return body;
    }

    private void assertPassedUntouched(TenantResolverFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        String what = request.getHeader("Host") + request.getRequestURI();
        assertThat(chain.seen).as(what).containsExactly(Optional.empty());
        assertThat(response.getStatus()).as(what).isEqualTo(200);
        assertThat(response.getContentType()).as(what).isNull();
        assertThat(response.getContentAsByteArray()).as(what).isEmpty();
    }

    private record Call(String method, String path, String query) {
        MockHttpServletRequest to(String host) {
            MockHttpServletRequest request = request(method, host, path, path);
            request.setQueryString(query);
            return request;
        }
    }

    @FunctionalInterface
    private interface ChainAction {
        void run() throws IOException, ServletException;
    }

    /** Records, per call, the Workspace bound while the chain runs. */
    private final class RecordingChain implements FilterChain {
        private final List<Optional<UUID>> seen = new ArrayList<>();
        private ChainAction action;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            seen.add(holder.currentWorkspaceId());
            if (action != null) {
                action.run();
            }
        }
    }

    /** A Registry in a map: rows change between requests, and every site-name lookup is counted. */
    private static final class FakeCatalog implements WorkspaceCatalog {
        private final Map<String, WorkspaceSummary> rows = new HashMap<>();
        private final Map<String, Integer> lookups = new HashMap<>();
        private RuntimeException failure;

        void put(UUID id, String siteName, WorkspaceStatus status) {
            rows.put(
                    siteName,
                    new WorkspaceSummary(
                            id,
                            siteName,
                            status,
                            siteName + ".example",
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()));
        }

        int lookups(String siteName) {
            return lookups.getOrDefault(siteName, 0);
        }

        int totalLookups() {
            return lookups.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public Optional<WorkspaceSummary> findBySiteName(String siteName) {
            lookups.merge(siteName, 1, Integer::sum);
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(rows.get(siteName));
        }

        @Override
        public Optional<WorkspaceSummary> findById(UUID workspaceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WorkspaceSummary> findActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkspaceSummary linkJiraSite(String siteUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claimEmail(String email) {
            throw new UnsupportedOperationException();
        }
    }
}
