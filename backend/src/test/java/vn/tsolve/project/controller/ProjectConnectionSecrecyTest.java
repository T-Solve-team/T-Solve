package vn.tsolve.project.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.repository.ProjectLinkRepository;
import vn.tsolve.project.repository.ProjectLinkRepository.CallerDepartment;
import vn.tsolve.project.repository.ProjectLinkRepository.Link;
import vn.tsolve.project.repository.ProjectRepository;
import vn.tsolve.project.service.impl.ProjectServiceImpl;
import vn.tsolve.tracker.client.JiraClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.impl.TrackerProjectServiceImpl;

/**
 * AC-001.17 end to end without a Spring context (D43): the real tracker service and Jira client talk to a fake Jira
 * that echoes the service account's credentials in its bodies, and the Project's answers are rendered by the real
 * controller and {@link GlobalExceptionHandler}. Nothing planted may reach a response or any log line.
 */
class ProjectConnectionSecrecyTest {
    private static final String EMAIL = "svc@acme.atlassian.test";
    private static final String TOKEN = "ATATT-planted-token";
    private static final String BODY_MARKER = "planted-secret-body";
    private static final String BASIC =
            Base64.getEncoder().encodeToString((EMAIL + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
    private static final List<String> PLANTED = List.of(EMAIL, TOKEN, BODY_MARKER, "errorMessages", BASIC);
    private static final String ERROR_BODY = "{\"errorMessages\":[\"" + BODY_MARKER + "\"],\"errors\":{\"email\":\""
            + EMAIL + "\",\"token\":\"" + TOKEN + "\"}}";
    private static final String STATUSES_BODY = "[{\"name\":\"Task\",\"description\":\"" + BODY_MARKER
            + "\",\"statuses\":[{\"id\":\"10002\",\"name\":\"Done\",\"description\":\"" + EMAIL
            + "\",\"statusCategory\":{\"key\":\"done\"}}]}]";
    // The fake Jira's own loggers report what it serves; they are the test's server, not T-Solve. The JDK's HTTP
    // client logs every request header at FINE (seen once a Spring context has bridged JUL to SLF4J); that is the
    // JDK's debug trace, off at the application's INFO level, not something T-Solve writes.
    private static final List<String> FAKE_SERVER_LOGGERS =
            List.of("com.github.tomakehurst", "wiremock", "org.eclipse", "sun.net.www");
    private static final UUID CALLER = new UUID(127, 11);

    private final WireMockServer jira = new WireMockServer(options().dynamicPort());
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final ProjectLinkRepository links = mock(ProjectLinkRepository.class);
    private Level previousLevel;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jira.start();
        JiraAccount account = new JiraAccount(jira.baseUrl(), EMAIL, TOKEN);
        TrackerProjectServiceImpl tracker = new TrackerProjectServiceImpl(
                () -> Optional.of(account), new JiraClient(Duration.ofSeconds(30), Duration.ofSeconds(30)));
        TenantScope scope = mock(TenantScope.class);
        when(scope.currentWorkspaceId()).thenReturn(new UUID(127, 100));
        when(links.findCallerDepartment(CALLER))
                .thenReturn(Optional.of(new CallerDepartment(1L, new UUID(127, 1), "Operations")));
        when(links.findLink(anyString())).thenReturn(Optional.empty());
        ProjectServiceImpl service = new ProjectServiceImpl(
                mock(ProjectRepository.class),
                links,
                tracker,
                scope,
                mock(AuditService.class),
                mock(PlatformTransactionManager.class));
        mvc = MockMvcBuilders.standaloneSetup(new ProjectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        TSolveUser user = new TSolveUser(CALLER, "pm@example.test", "Project Manager", Set.of(Role.PROJECT_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        previousLevel = root.getLevel();
        root.setLevel(Level.ALL);
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(logs);
        root.setLevel(previousLevel);
        logs.stop();
        SecurityContextHolder.clearContext();
        jira.stop();
    }

    static Stream<Arguments> jiraAnswers() {
        String found = "{\"key\":\"OPS\",\"description\":\"" + BODY_MARKER + "\",\"lead\":{\"emailAddress\":\"" + EMAIL
                + "\"}}";
        String canonical = found.replace("\"OPS\"", "\"LEGACY\"");
        return Stream.of(
                Arguments.of("found", 200, found, 200, 200, null),
                Arguments.of("canonical key linked", 200, canonical, 200, 409, "MSG49"),
                Arguments.of("401", 401, ERROR_BODY, 200, 409, "MSG48"),
                Arguments.of("403", 403, ERROR_BODY, 200, 409, "MSG48"),
                Arguments.of("404", 404, ERROR_BODY, 200, 409, "MSG48"),
                Arguments.of("500", 500, ERROR_BODY, 200, 503, "MSG06"),
                Arguments.of("statuses 500", 200, found, 500, 503, "MSG06"),
                Arguments.of("200 without key", 200, ERROR_BODY, 200, 503, "MSG06"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jiraAnswers")
    void keepsCredentialsAndJiraBodiesOutOfResponsesAndLogs_AC_001_17(
            String path, int projectStatus, String projectBody, int statusesStatus, int expected, String code)
            throws Exception {
        String canonicalKey = projectBody.contains("LEGACY") ? "LEGACY" : "OPS";
        jira.stubFor(get(urlEqualTo("/rest/api/3/project/OPS")).willReturn(json(projectStatus, projectBody)));
        jira.stubFor(get(urlEqualTo("/rest/api/3/project/" + canonicalKey + "/statuses"))
                .willReturn(json(statusesStatus, statusesStatus == 200 ? STATUSES_BODY : ERROR_BODY)));
        when(links.findLink("LEGACY"))
                .thenReturn(Optional.of(
                        new Link(new UUID(127, 10), 2, "Canonical", "LEGACY", "ACTIVE", null, null, CALLER, false)));

        MvcResult result = mvc.perform(post("/api/v1/projects/connection-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jiraProjectKey\":\"OPS\"}"))
                .andReturn();

        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(response).isEqualTo(expected);
        if (code != null) {
            assertThat(response).contains("\"code\":\"" + code + "\"");
        }
        assertThat(jira.getAllServeEvents()).as("the fake Jira was called").isNotEmpty();
        List<String> seen = new ArrayList<>(List.of(response));
        result.getResponse()
                .getHeaderNames()
                .forEach(name -> seen.add(name + ": " + result.getResponse().getHeaders(name)));
        logs.list.stream()
                .filter(event -> FAKE_SERVER_LOGGERS.stream().noneMatch(event.getLoggerName()::startsWith))
                .forEach(event -> {
                    seen.add(event.getFormattedMessage());
                    seen.add(String.valueOf(event.getMessage()));
                    seen.add(Arrays.deepToString(event.getArgumentArray()));
                    if (event.getThrowableProxy() != null) {
                        seen.add(ThrowableProxyUtil.asString(event.getThrowableProxy()));
                    }
                });
        assertThat(logs.list)
                .as("T-Solve's own tracker logs were captured, not only the fake Jira's")
                .anyMatch(event -> event.getLoggerName().startsWith("vn.tsolve.tracker"));
        for (String text : seen) {
            for (String planted : PLANTED) {
                assertThat(text)
                        .as("[%s] must not contain a planted value", path)
                        .doesNotContain(planted);
            }
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
