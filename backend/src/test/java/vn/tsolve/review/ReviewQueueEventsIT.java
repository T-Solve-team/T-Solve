package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;

/** Review Queue event stream over SSE, signalled by a tenant NOTIFY (STORY-012, KAN-154). */
@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "tsolve.review.events.heartbeat=PT1S"
        })
@AutoConfigureMockMvc
@Import({TwoTenantDatabases.class, ReviewQueueEventsIT.WorkspaceAccessConfiguration.class})
@WithTSolveUser(id = ReviewQueueEventsIT.MANAGER_ID, roles = Role.PROJECT_MANAGER)
class ReviewQueueEventsIT {

    static final String MANAGER_ID = "3c1f8a52-7d0e-4b6a-9a11-0d2f6b8e4c71";
    static final String NO_PROJECT_MANAGER_ID = "9a7e2d40-5b3c-4f18-8e6d-1c4a7b9f2e03";
    private static final UUID MANAGER = UUID.fromString(MANAGER_ID);
    private static final UUID NO_PROJECT_MANAGER = UUID.fromString(NO_PROJECT_MANAGER_ID);
    static final String REVOCABLE_ID = "5b2d9c61-3e47-4a80-b1f5-7a6c0d3e8f12";
    static final String REVOCABLE_BETA_ID = "6c3eadb2-4f58-4b91-82a6-8b7d1e4f9a23";
    private static final UUID REVOCABLE = UUID.fromString(REVOCABLE_ID);
    private static final UUID REVOCABLE_BETA = UUID.fromString(REVOCABLE_BETA_ID);
    /** Projects the revocable test accounts still manage; a test removes them to revoke access mid-stream. */
    private static final Map<UUID, Set<UUID>> REVOCABLE_PROJECTS = new ConcurrentHashMap<>();

    private static final UUID OPS = new UUID(0, 710);
    private static final UUID SUPPORT = new UUID(0, 711);
    private static final UUID UNMANAGED = new UUID(0, 712);
    private static final Pattern DATA = Pattern.compile("(?m)^data:(.*)$");
    private static final long WAIT_MS = 5_000;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope workspace;

    private final ObjectMapper json = new ObjectMapper();

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkspaceAccessConfiguration {

        @Bean
        @Primary
        ReviewWorkspaceAccess reviewWorkspaceAccess(TwoTenantDatabases.RoutingTenantScope scope) {
            return new ReviewWorkspaceAccess() {
                @Override
                public Optional<Set<UUID>> managedProjectIds(UUID accountId) {
                    if (!verified(accountId)) {
                        return Optional.empty();
                    }
                    if (accountId.equals(REVOCABLE) || accountId.equals(REVOCABLE_BETA)) {
                        return Optional.of(REVOCABLE_PROJECTS.getOrDefault(accountId, Set.of()));
                    }
                    return Optional.of(accountId.equals(MANAGER) ? Set.of(OPS, SUPPORT) : Set.of());
                }

                @Override
                public Optional<Map<UUID, ProjectAccess>> readableProjects(UUID accountId) {
                    if (!verified(accountId)) {
                        return Optional.empty();
                    }
                    return Optional.of(Map.of(
                            OPS, new ProjectAccess(new Project(OPS, "Operations"), new UUID(0, 810), true),
                            SUPPORT, new ProjectAccess(new Project(SUPPORT, "Support"), new UUID(0, 811), true),
                            UNMANAGED, new ProjectAccess(new Project(UNMANAGED, "Finance"), new UUID(0, 812), false)));
                }

                private boolean verified(UUID accountId) {
                    try {
                        UUID current = scope.currentWorkspaceId();
                        if (accountId.equals(REVOCABLE_BETA)) {
                            return current.equals(TwoTenantDatabases.BETA);
                        }
                        return current.equals(TwoTenantDatabases.ACME)
                                && (accountId.equals(MANAGER)
                                        || accountId.equals(NO_PROJECT_MANAGER)
                                        || accountId.equals(REVOCABLE));
                    } catch (IllegalStateException unbound) {
                        return false;
                    }
                }
            };
        }
    }

    /** An open event stream: the MockMvc response keeps accumulating while the emitter sends. */
    private final class Stream {
        private final MvcResult result;

        Stream(MvcResult result) {
            this.result = result;
        }

        String body() throws Exception {
            return result.getResponse().getContentAsString();
        }

        List<JsonNode> events() throws Exception {
            List<JsonNode> events = new ArrayList<>();
            Matcher matcher = DATA.matcher(body());
            while (matcher.find()) {
                events.add(json.readTree(matcher.group(1).trim()));
            }
            return events;
        }

        int eventNames() throws Exception {
            return body().split("event:\\s*queue-changed", -1).length - 1;
        }

        void awaitHeartbeat() throws Exception {
            awaitUntil(() -> body().contains(":hb"));
        }

        void awaitEventFor(UUID project) throws Exception {
            awaitUntil(() -> events().stream()
                    .anyMatch(e -> project.toString().equals(e.path("projectId").asText())));
        }

        List<String> projectIds() throws Exception {
            return events().stream().map(e -> e.path("projectId").asText()).toList();
        }
    }

    private interface Condition {
        boolean holds() throws Exception;
    }

    private static void awaitUntil(Condition condition) throws Exception {
        awaitUntil(condition, WAIT_MS);
    }

    private static void awaitUntil(Condition condition, long waitMs) throws Exception {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.holds()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.holds()).as("condition within %d ms", waitMs).isTrue();
    }

    /** Backends of the database that sit on the Review Queue LISTEN. */
    private static int listenSessions(JdbcTemplate db) {
        return db.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and query = 'LISTEN review_queue'",
                Integer.class);
    }

    /** Opens the stream as the default manager in ACME and waits until the listener is surely attached. */
    private Stream openReady() throws Exception {
        Stream stream = open();
        stream.awaitHeartbeat();
        return stream;
    }

    private Stream open() throws Exception {
        ResultActions actions = inAcme(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM));
        actions.andExpect(request().asyncStarted());
        return new Stream(actions.andReturn());
    }

    @Test
    void streamOpensAsEventStreamAndSendsHeartbeats_AC_012_1() throws Exception {
        Stream stream = open();
        assertThat(stream.result.getResponse().getContentType()).startsWith("text/event-stream");
        stream.awaitHeartbeat();
        int first = countOf(stream.body(), ":hb");
        awaitUntil(() -> countOf(stream.body(), ":hb") > first);
        assertThat(stream.eventNames()).isZero();
    }

    @Test
    void ticketEnteringPendingReviewSendsQueueChanged_AC_012_2() throws Exception {
        Stream stream = openReady();
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-IN-" + tag(), "PENDING_REVIEW");
        long start = System.currentTimeMillis();
        stream.awaitEventFor(OPS);
        assertThat(System.currentTimeMillis() - start).isLessThan(WAIT_MS);
        assertThat(stream.body()).contains("event:queue-changed");
        clean(ticket);
    }

    @Test
    void ticketUpdatedToPendingReviewSendsQueueChanged_AC_012_2() throws Exception {
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "EVT-UP-" + tag(), "REJECTED");
        Stream stream = openReady();
        TwoTenantDatabases.tenantA().update("update ticket set status = 'PENDING_REVIEW' where ticket_id = ?", ticket);
        stream.awaitEventFor(SUPPORT);
        assertThat(stream.projectIds()).containsExactly(SUPPORT.toString());
        clean(ticket);
    }

    @Test
    void decisionMovingTicketOutOfPendingReviewSendsQueueChanged_AC_012_2() throws Exception {
        // review_decision.project_manager_id references app_user, so the deciding manager must exist.
        TwoTenantDatabases.tenantA()
                .update(
                        "insert into app_user (id, email, status, created_at) values (?, 'events-manager@example.test', 'ACTIVE', now()) on conflict (id) do nothing",
                        MANAGER);
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-OUT-" + tag(), "PENDING_REVIEW");
        Stream stream = openReady();
        inAcme(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"ticketIds\":[\"" + ticket + "\"],\"comment\":\"Attach the log\"}"))
                .andExpect(status().isOk());
        stream.awaitEventFor(OPS);
        assertThat(stream.projectIds()).containsExactly(OPS.toString());
        clean(ticket);
    }

    @Test
    void edge1_ticketMovedToAnotherProjectWhilePendingNotifiesNewAndOldProject_AC_012_2() throws Exception {
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-MV-" + tag(), "PENDING_REVIEW");
        Stream stream = openReady();
        TwoTenantDatabases.tenantA().update("update ticket set project_id = ? where ticket_id = ?", SUPPORT, ticket);
        stream.awaitEventFor(OPS);
        stream.awaitEventFor(SUPPORT);
        assertThat(stream.projectIds()).containsExactlyInAnyOrder(OPS.toString(), SUPPORT.toString());
        clean(ticket);
    }

    @Test
    void edge2_noEventForAnUnmanagedProject_AC_012_3() throws Exception {
        Stream stream = openReady();
        UUID hidden = ticket(TwoTenantDatabases.tenantA(), UNMANAGED, "EVT-UM-" + tag(), "PENDING_REVIEW");
        UUID sentinel = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-UMS-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(OPS);
        assertThat(stream.projectIds()).containsExactly(OPS.toString());
        clean(hidden);
        clean(sentinel);
    }

    @Test
    void edge3_noEventForAChangeInAnotherWorkspace_AC_012_3() throws Exception {
        Stream stream = openReady();
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        UUID other = ticket(beta, OPS, "EVT-BETA-" + tag(), "PENDING_REVIEW");
        UUID sentinel = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "EVT-BS-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(SUPPORT);
        assertThat(stream.projectIds()).containsExactly(SUPPORT.toString());
        beta.update("delete from ticket where ticket_id = ?", other);
        clean(sentinel);
    }

    @Test
    void edge4_rolledBackTransactionSendsNothing_AC_012_3() throws Exception {
        Stream stream = openReady();
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(TwoTenantDatabases.tenantA().getDataSource()));
        tx.executeWithoutResult(status -> {
            JdbcTemplate acme = new JdbcTemplate(TwoTenantDatabases.tenantA().getDataSource());
            ticket(acme, OPS, "EVT-RB-" + tag(), "PENDING_REVIEW");
            status.setRollbackOnly();
        });
        UUID sentinel = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "EVT-RBS-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(SUPPORT);
        assertThat(stream.projectIds()).containsExactly(SUPPORT.toString());
        clean(sentinel);
    }

    @Test
    void edge5_transactionChangingSeveralTicketsOfOneProjectSendsOneEvent_AC_012_3() throws Exception {
        Stream stream = openReady();
        List<UUID> created = new ArrayList<>();
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(TwoTenantDatabases.tenantA().getDataSource()));
        tx.executeWithoutResult(status -> {
            JdbcTemplate acme = new JdbcTemplate(TwoTenantDatabases.tenantA().getDataSource());
            for (int i = 0; i < 3; i++) {
                created.add(ticket(acme, OPS, "EVT-BATCH-" + i + "-" + tag(), "PENDING_REVIEW"));
            }
        });
        UUID sentinel = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "EVT-BATS-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(SUPPORT);
        assertThat(stream.projectIds()).containsExactlyInAnyOrder(OPS.toString(), SUPPORT.toString());
        assertThat(stream.eventNames()).isEqualTo(2);
        created.forEach(this::clean);
        clean(sentinel);
    }

    @Test
    void edge6_payloadCarriesOnlyTheProjectId_AC_012_3() throws Exception {
        Stream stream = openReady();
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-PAY-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(OPS);
        JsonNode event = stream.events().get(0);
        assertThat(event.fieldNames()).toIterable().containsExactly("projectId");
        assertThat(stream.body())
                .doesNotContain("Login fails")
                .doesNotContain("Restart")
                .doesNotContain(ticket.toString());
        clean(ticket);
    }

    @Test
    @WithTSolveUser(id = REVOCABLE_BETA_ID, roles = Role.PROJECT_MANAGER)
    void edge7_lastUnsubscribeClosesTheListenerConnection_AC_012_1() throws Exception {
        REVOCABLE_PROJECTS.put(REVOCABLE_BETA, Set.of(OPS));
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        assertThat(listenSessions(beta)).isZero();
        ResultActions actions = workspace.runIn(TwoTenantDatabases.BETA, () -> {
            try {
                return mvc.perform(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        actions.andExpect(request().asyncStarted());
        Stream stream = new Stream(actions.andReturn());
        stream.awaitHeartbeat();
        assertThat(listenSessions(beta)).isEqualTo(1);

        REVOCABLE_PROJECTS.remove(REVOCABLE_BETA); // the stream ends at its next heartbeat: last subscriber leaves
        awaitUntil(() -> listenSessions(beta) == 0);
    }

    @Test
    void edge8_listenerReconnectResyncsEveryProjectOfTheStream_AC_012_2() throws Exception {
        Stream stream = openReady();
        assertThat(stream.eventNames()).isZero();
        TwoTenantDatabases.tenantA()
                .queryForList("select pg_terminate_backend(pid) from pg_stat_activity"
                        + " where datname = current_database() and query = 'LISTEN review_queue'");
        awaitUntil(() -> stream.projectIds().containsAll(List.of(OPS.toString(), SUPPORT.toString())), 15_000);
    }

    @Test
    @WithTSolveUser(id = REVOCABLE_ID, roles = Role.PROJECT_MANAGER)
    void edge9_projectNoLongerManagedStopsEventsAfterTheNextHeartbeat_AC_012_3() throws Exception {
        REVOCABLE_PROJECTS.put(REVOCABLE, Set.of(OPS, SUPPORT));
        Stream stream = openReady();
        REVOCABLE_PROJECTS.put(REVOCABLE, Set.of(OPS));
        int beats = countOf(stream.body(), ":hb");
        awaitUntil(() -> countOf(stream.body(), ":hb") >= beats + 2); // a heartbeat after the change re-read it
        UUID gone = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "EVT-RV-" + tag(), "PENDING_REVIEW");
        UUID sentinel = ticket(TwoTenantDatabases.tenantA(), OPS, "EVT-RVS-" + tag(), "PENDING_REVIEW");
        stream.awaitEventFor(OPS);
        assertThat(stream.projectIds()).containsExactly(OPS.toString());

        REVOCABLE_PROJECTS.remove(REVOCABLE);
        Thread.sleep(2_500); // the stream completes at its next heartbeat and sends nothing after that
        int atEnd = countOf(stream.body(), ":hb");
        Thread.sleep(2_500);
        assertThat(countOf(stream.body(), ":hb")).isEqualTo(atEnd);
        clean(gone);
        clean(sentinel);
    }

    @Test
    @WithAnonymousUser
    void deniesAnonymousCallers_AC_012_4() throws Exception {
        inAcme(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithTSolveUser(id = MANAGER_ID, roles = Role.EMPLOYEE)
    void deniesCallersWithoutProjectManager_AC_012_4() throws Exception {
        inAcme(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithTSolveUser(id = NO_PROJECT_MANAGER_ID, roles = Role.PROJECT_MANAGER)
    void deniesManagerWithoutManagedProjects_AC_012_4() throws Exception {
        inAcme(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesUnverifiedWorkspaceAccount_AC_012_4() throws Exception {
        mvc.perform(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden());
        workspace.runIn(TwoTenantDatabases.BETA, () -> {
            try {
                return mvc.perform(get("/api/v1/review-queue/events").accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(status().isForbidden());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private ResultActions inAcme(RequestBuilder request) {
        return workspace.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static int countOf(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void clean(UUID ticket) {
        TwoTenantDatabases.tenantA().update("delete from review_decision where ticket_id = ?", ticket);
        TwoTenantDatabases.tenantA().update("delete from ticket where ticket_id = ?", ticket);
    }

    private static UUID ticket(JdbcTemplate db, UUID project, String key, String status) {
        return db.queryForObject(
                """
                insert into ticket (source_ticket_id, project_id, source, status, writer, title, symptoms,
                                    root_cause, resolution_steps, scope)
                values (?, ?, 'SOLUTION_FORM', ?, 'Minh Tran', 'Login fails', 'Error', 'Token', 'Restart', 'Users')
                returning ticket_id
                """,
                UUID.class,
                key,
                project,
                status);
    }
}
