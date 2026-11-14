package vn.tsolve.platform.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.platform.isolation.IsolationFixtures.TenantData;

/** STORY-015: real host-bound sessions, real tenant routing, and real module repositories; AI is deterministic. */
@Import(IsolationFixtures.ScopeConfiguration.class)
class CrossModuleIsolationIT extends AbstractSessionIT {
    // Current controller mapping (there is no /api/v1/reviews/review-queue alias).
    private static final String REVIEW_QUEUE = "/api/v1/review-queue";
    private static final String FEEDBACK = "{\"isHelpful\":true,\"comment\":\"Isolation check\"}";
    private static final String QUESTION = "{\"question\":\"" + IsolationFixtures.QUESTION + "\",\"history\":[]}";

    @Autowired
    private WebSessionStore sessions;

    @Autowired
    private ObjectMapper json;

    // Boot 3.3 / Spring 6.1 provides @MockBean, not Spring 6.2's @MockitoBean.
    @MockBean
    private LlmClient llm;

    @MockBean
    private EmbeddingClient embeddings;

    @Captor
    private ArgumentCaptor<List<ChatMessage>> messages;

    private TenantData acme;
    private TenantData beta;
    private Cookie resolverSession;
    private Cookie managerSession;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedBothWorkspaces() {
        acme = IsolationFixtures.seed(TwoTenantDatabases.tenantA(), "ACME");
        beta = IsolationFixtures.seed(TwoTenantDatabases.tenantB(), "BETA");
        resolverSession = session(acme.resolver());
        managerSession = session(acme.manager());
        when(embeddings.embed(any())).thenReturn(List.of(IsolationFixtures.vector()));
        when(llm.chat(any(), isNull())).thenReturn("Follow the documented resolution [1].");
    }

    @AfterEach
    void cleanOwnRows() {
        IsolationFixtures.clean(TwoTenantDatabases.tenantA(), acme);
        IsolationFixtures.clean(TwoTenantDatabases.tenantB(), beta);
    }

    @Test
    void session_readsRowExistingOnlyInAcme_AC_015_1() throws Exception {
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject(
                                "SELECT count(*) FROM ticket WHERE ticket_id = ?", Long.class, acme.published()))
                .isZero();
        JsonNode detail = ok(inAcme(get("/api/v1/tickets/{id}", acme.published()), resolverSession));
        assertThat(detail.path("ticketId").asText()).isEqualTo(acme.published().toString());
        assertThat(detail.toString()).contains(acme.marker()).doesNotContain(beta.marker());
    }

    @Test
    void ticketList_listsOnlyAcmeTickets_AC_015_2() throws Exception {
        JsonNode body = ok(inAcme(get("/api/v1/tickets"), resolverSession));
        assertThat(body.path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(body.path("content")).hasSize(1);
        assertThat(body.path("content").get(0).path("ticketId").asText())
                .isEqualTo(acme.published().toString());
        assertThat(body.path("content").get(0).path("title").asText()).isEqualTo(IsolationFixtures.TITLE);
        assertThat(body.toString())
                .doesNotContain(beta.published().toString(), beta.pending().toString(), beta.marker());
    }

    @Test
    void ticketDetail_betaIdLooksExactlyLikeUnknownId_AC_015_3() throws Exception {
        Map<String, Long> before = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB());
        MockHttpServletResponse unknown = inAcme(get("/api/v1/tickets/{id}", UUID.randomUUID()), resolverSession);
        MockHttpServletResponse foreign = inAcme(get("/api/v1/tickets/{id}", beta.published()), resolverSession);
        sameMissingAnswer(unknown, foreign);
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB())).isEqualTo(before);
    }

    @Test
    void feedback_betaIdLooksExactlyLikeUnknownIdAndWritesNothing_AC_015_3() throws Exception {
        Map<String, Long> before = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB());
        Map<String, Long> acmeBefore = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA());
        MockHttpServletResponse unknown = inAcme(feedback(UUID.randomUUID()), resolverSession);
        MockHttpServletResponse foreign = inAcme(feedback(beta.published()), resolverSession);
        sameMissingAnswer(unknown, foreign);
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB())).isEqualTo(before);
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA())).isEqualTo(acmeBefore);
    }

    @Test
    void search_returnsOnlyAcmeTicketsAndLogsInAcme_AC_015_4() throws Exception {
        Map<String, Long> betaBefore = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB());
        long before = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA()).get("search_log");
        // Search's current query parameter is q; keyword belongs to the Ticket List.
        JsonNode body = ok(inAcme(get("/api/v1/search/tickets").param("q", IsolationFixtures.TITLE), resolverSession));
        assertThat(body.path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(body.path("content")).hasSize(1);
        assertThat(body.path("content").get(0).path("ticketId").asText())
                .isEqualTo(acme.published().toString());
        assertThat(body.toString()).doesNotContain(beta.published().toString(), beta.marker());
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA()).get("search_log"))
                .isEqualTo(before + 1);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForMap(
                                "SELECT user_id, query, result_count FROM search_log WHERE user_id = ?",
                                acme.resolver()))
                .containsEntry("user_id", acme.resolver())
                .containsEntry("query", IsolationFixtures.TITLE)
                .containsEntry("result_count", 1);
        verify(embeddings).embed(List.of(IsolationFixtures.TITLE));
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB())).isEqualTo(betaBefore);
    }

    @Test
    void chatbot_contextCitationsAndStoredAnswerBelongOnlyToAcme_AC_015_5() throws Exception {
        Map<String, Long> betaBefore = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB());
        long before = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA()).get("chatbot_answer");
        JsonNode body = ok(inAcme(question(), resolverSession));
        verify(llm).chat(messages.capture(), isNull());
        String context = messages.getValue().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
        assertThat(context).contains(acme.marker()).doesNotContain(beta.marker());
        assertThat(body.path("outcome").asText()).isEqualTo("ANSWERED");
        assertThat(body.path("citations")).hasSize(1);
        JsonNode solution = body.path("citations").get(0).path("solution");
        assertThat(solution.path("symptoms").asText()).isEqualTo(acme.marker() + " symptoms");
        assertThat(solution.path("rootCause").asText()).isEqualTo(acme.marker() + " root cause");
        assertThat(solution.path("resolutionSteps").asText()).isEqualTo(acme.marker() + " resolution");
        assertThat(body.path("citations").toString()).doesNotContain(beta.marker());
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA()).get("chatbot_answer"))
                .isEqualTo(before + 1);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForMap(
                                "SELECT user_id, question, outcome, answer_text FROM chatbot_answer WHERE chatbot_answer_id = ?",
                                UUID.fromString(body.path("answerId").asText())))
                .containsEntry("user_id", acme.resolver())
                .containsEntry("question", IsolationFixtures.QUESTION)
                .containsEntry("outcome", "ANSWERED")
                .containsEntry("answer_text", body.path("text").asText());
        verify(embeddings).embed(List.of(IsolationFixtures.QUESTION));
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB())).isEqualTo(betaBefore);
    }

    @Test
    void reviewQueue_listsOnlyAcmeTicketsAsProjectManager_AC_015_6() throws Exception {
        JsonNode body = ok(inAcme(get(REVIEW_QUEUE), managerSession));
        assertThat(body.path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(body.path("content")).hasSize(1);
        assertThat(body.path("content").get(0).path("ticketId").asText())
                .isEqualTo(acme.pending().toString());
        assertThat(body.toString())
                .doesNotContain(beta.pending().toString(), beta.published().toString(), beta.marker());
    }

    enum Endpoint {
        TICKET_LIST,
        TICKET_DETAIL,
        TICKET_FEEDBACK,
        SEARCH,
        CHATBOT,
        REVIEW_QUEUE
    }

    static Stream<Endpoint> protectedEndpoints() {
        return Stream.of(Endpoint.values());
    }

    @ParameterizedTest(name = "{0} without a Workspace session")
    @MethodSource("protectedEndpoints")
    void missingSession_returns401AndWritesNothingInEitherWorkspace_AC_015_7(Endpoint endpoint) throws Exception {
        Map<String, Long> beforeA = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA());
        Map<String, Long> beforeB = IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB());
        MockHttpServletRequestBuilder request =
                switch (endpoint) {
                    case TICKET_LIST -> get("/api/v1/tickets");
                    case TICKET_DETAIL -> get("/api/v1/tickets/{id}", acme.published());
                    case TICKET_FEEDBACK -> feedback(acme.published());
                    case SEARCH -> get("/api/v1/search/tickets").param("q", IsolationFixtures.TITLE);
                    case CHATBOT -> question();
                    case REVIEW_QUEUE -> get(REVIEW_QUEUE);
                };
        // Valid CSRF on writes keeps this a missing-session check rather than a missing-CSRF check.
        MockHttpServletResponse response = perform(onAcmeHost(request));
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(401);
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantA())).isEqualTo(beforeA);
        assertThat(IsolationFixtures.rowCounts(TwoTenantDatabases.tenantB())).isEqualTo(beforeB);
        verifyNoInteractions(embeddings, llm);
    }

    private Cookie session(UUID account) {
        String value = sessions.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                TwoTenantDatabases.ACME,
                account,
                null,
                InMemoryWorkspaceLookup.ACME.siteHost(),
                "test-id-token",
                clock.instant(),
                clock.instant()));
        return new Cookie(SessionTestFixtures.SESSION_COOKIE, value);
    }

    private MockHttpServletResponse inAcme(MockHttpServletRequestBuilder request, Cookie cookie) throws Exception {
        return perform(onAcmeHost(request).cookie(cookie));
    }

    private static MockHttpServletRequestBuilder onAcmeHost(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setServerName(InMemoryWorkspaceLookup.ACME.siteHost());
            servletRequest.setServerPort(SessionTestFixtures.PORT);
            servletRequest.setScheme("http");
            return servletRequest;
        });
    }

    private static MockHttpServletRequestBuilder feedback(UUID ticket) {
        return put("/api/v1/tickets/{id}/feedback", ticket)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FEEDBACK);
    }

    private static MockHttpServletRequestBuilder question() {
        return post("/api/v1/chatbot/answers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUESTION);
    }

    private JsonNode ok(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private void sameMissingAnswer(MockHttpServletResponse unknown, MockHttpServletResponse foreign) throws Exception {
        assertThat(unknown.getStatus()).as(unknown.getContentAsString()).isEqualTo(404);
        JsonNode unknownCode = json.readTree(unknown.getContentAsString()).get("code");
        assertThat(unknownCode).isNotNull();
        assertThat(unknownCode.asText()).isNotBlank();
        assertThat(foreign.getStatus()).as(foreign.getContentAsString()).isEqualTo(unknown.getStatus());
        assertThat(json.readTree(foreign.getContentAsString()).get("code")).isEqualTo(unknownCode);
    }
}
