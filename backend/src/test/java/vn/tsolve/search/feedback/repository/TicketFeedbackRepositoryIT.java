package vn.tsolve.search.feedback.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;
import vn.tsolve.search.feedback.entity.TicketFeedback;
import vn.tsolve.search.feedback.service.TicketFeedbackService;
import vn.tsolve.search.scope.service.VisibleScope;

@SpringBootTest
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "TSOLVE_JIRA_APP_ID=test-app",
            "FORGE_JWKS_URI=http://localhost:9999/jwks",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "OLLAMA_BASE_URL=http://127.0.0.1:11434",
            "OLLAMA_CHAT_MODEL=test",
            "SEARCH_DEV_SCOPE=",
            "SEARCH_EMBED_CATCH_UP_DELAY=1h"
        })
class TicketFeedbackRepositoryIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final TSolveUser USER =
            new TSolveUser(UUID.randomUUID(), "resolver@example.test", "Resolver", Set.of(Role.TICKET_RESOLVER));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketFeedbackService service;

    @Autowired
    private TicketFeedbackRepository feedback;

    @MockBean
    private VisibleScope scope;

    private final UUID publicId = UUID.randomUUID();
    private long ticket;
    private long department;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE ticket_feedback, ticket, project, department CASCADE");
        jdbc.update(
                """
                INSERT INTO app_user(id, email, full_name, status, created_at)
                VALUES (?, ?, 'Resolver', 'ACTIVE', now()) ON CONFLICT (id) DO NOTHING
                """,
                USER.id(),
                USER.email());
        department = jdbc.queryForObject(
                "INSERT INTO department(name,status) VALUES ('IT','ACTIVE') RETURNING department_id", Long.class);
        long project = jdbc.queryForObject(
                "INSERT INTO project(department_id,jira_project_key) VALUES (?,'ITSUP') RETURNING project_id",
                Long.class,
                department);
        ticket = jdbc.queryForObject(
                """
                INSERT INTO ticket(ticket_id, public_id, project_id, source, status, title,
                    symptoms, root_cause, resolution_steps, scope)
                VALUES ('IT-1', ?, ?, 'CSV', 'PUBLISHED', 'Example', 'Symptoms', 'Cause', 'Steps', 'Scope')
                RETURNING id
                """,
                Long.class,
                publicId,
                project);
        when(scope.of(any())).thenReturn(new VisibleScope.Scope(Set.of(department), Set.of(), Optional.empty()));
    }

    @Test
    void replacesFeedbackOfSameUser_AC_008_2() {
        UUID otherUser = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO app_user(id, email, status, created_at) VALUES (?, ?, 'ACTIVE', now())
                """,
                otherUser,
                otherUser + "@example.test");
        jdbc.update(
                """
                INSERT INTO ticket_feedback(ticket_id, user_id, rating, comment, channel, created_at, updated_at)
                VALUES (?, ?, 'HELPFUL', 'Earlier', 'ISSUE_PANEL', '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')
                """,
                ticket,
                USER.id());
        TicketFeedback original = ownFeedback();
        service.give(USER, publicId, new TicketFeedbackRequest(false, "  Replaced  "));

        TicketFeedback replaced = ownFeedback();
        assertThat(feedback.count()).isEqualTo(1);
        assertThat(replaced.getId()).isEqualTo(original.getId());
        assertThat(replaced.getTicketId()).isEqualTo(ticket);
        assertThat(replaced.getUserId()).isEqualTo(USER.id());
        assertThat(replaced.getRating()).isEqualTo("NOT_HELPFUL");
        assertThat(replaced.getComment()).isEqualTo("Replaced");
        assertThat(replaced.getChannel()).isEqualTo("WEB");
        assertThat(replaced.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(replaced.getUpdatedAt()).isAfter(original.getUpdatedAt());

        TSolveUser other = new TSolveUser(otherUser, "other@example.test", "Other", Set.of(Role.PROJECT_MANAGER));
        service.give(other, publicId, new TicketFeedbackRequest(true, null));
        service.give(USER, publicId, new TicketFeedbackRequest(true, " \t "));
        assertThat(feedback.count()).isEqualTo(2);
        assertThat(ownFeedback().getComment()).isNull();
        assertThat(ownFeedback().getCreatedAt()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void keepsOneRowForConcurrentPuts_AC_008_2() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var helpful = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                service.give(USER, publicId, new TicketFeedbackRequest(true, "First"));
                return null;
            });
            var notHelpful = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                service.give(USER, publicId, new TicketFeedbackRequest(false, "Second"));
                return null;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            helpful.get(20, TimeUnit.SECONDS);
            notHelpful.get(20, TimeUnit.SECONDS);
            assertThat(feedback.count()).isEqualTo(1);
            TicketFeedback stored = ownFeedback();
            assertThat(stored.getRating() + ":" + stored.getComment()).isIn("HELPFUL:First", "NOT_HELPFUL:Second");
            assertThat(stored.getChannel()).isEqualTo("WEB");
            assertThat(stored.getTicketId()).isEqualTo(ticket);
            assertThat(stored.getUpdatedAt()).isNotNull();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void answers404ForUnpublishedOrDeletedDepartment_AC_008_3() {
        jdbc.update("UPDATE ticket SET status = 'UNPUBLISHED' WHERE id = ?", ticket);
        assertNotSearchable();
        jdbc.update("UPDATE ticket SET status = 'PUBLISHED' WHERE id = ?", ticket);
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", department);
        assertNotSearchable();
        assertThat(feedback.count()).isZero();
    }

    private void assertNotSearchable() {
        assertThatThrownBy(() -> service.give(USER, publicId, new TicketFeedbackRequest(true, null)))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
    }

    private TicketFeedback ownFeedback() {
        return feedback.findAll().stream()
                .filter(row -> USER.id().equals(row.getUserId()))
                .findFirst()
                .orElseThrow();
    }
}
