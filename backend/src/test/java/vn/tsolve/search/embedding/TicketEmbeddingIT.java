package vn.tsolve.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ImportConfirmService;

@SpringBootTest
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=pm:1",
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "OLLAMA_BASE_URL=http://127.0.0.1:11434",
            "OLLAMA_CHAT_MODEL=test",
            "OLLAMA_EMBED_MODEL=bge-m3",
            "SEARCH_DEV_SCOPE=",
            "SEARCH_EMBED_BATCH=4",
            "SEARCH_EMBED_CATCH_UP_DELAY=24h"
        })
class TicketEmbeddingIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private CsvImportService imports;

    @Autowired
    private ImportConfirmService confirms;

    @Autowired
    private TicketEmbeddingService service;

    @Autowired
    private TicketEmbeddingRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEmbeddings client;

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @Primary
        TestEmbeddings testEmbeddings(JdbcTemplate jdbc) {
            return new TestEmbeddings(jdbc);
        }
    }

    static class TestEmbeddings implements EmbeddingClient {
        private final JdbcTemplate jdbc;
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch called = new CountDownLatch(1);
        private volatile boolean fail;
        private volatile float component = 1f;

        TestEmbeddings(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            calls.add(new Call(
                    Thread.currentThread().threadId(),
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    jdbc.queryForObject("SELECT count(*) FROM ticket", Integer.class),
                    List.copyOf(texts)));
            called.countDown();
            if (fail) {
                throw new AiUnavailableException();
            }
            return texts.stream()
                    .map(text -> {
                        float[] vector = new float[DIMENSIONS];
                        vector[0] = component;
                        return vector;
                    })
                    .toList();
        }
    }

    private record Call(long threadId, boolean inTransaction, int committedTickets, List<String> texts) {}

    @BeforeEach
    void seed() {
        jdbc.execute(
                "TRUNCATE ticket_import_decision, ticket, import_batch, import_preview, project, department CASCADE");
        jdbc.update("INSERT INTO department (department_id, name) VALUES (1, 'Embedding IT')");
        jdbc.update("INSERT INTO project (project_id, department_id, jira_project_key) VALUES (1, 1, 'EMBED')");
        client.calls.clear();
        client.called = new CountDownLatch(1);
        client.fail = false;
        client.component = 1f;
    }

    @Test
    void storesVectorAfterImportCommits_AC_005_4() {
        long importingThread = Thread.currentThread().threadId();
        assertThat(confirms.confirm("pm", upload("EMBED-1", "EMBED-2")).created())
                .isEqualTo(2);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(embeddingCount()).isEqualTo(2));

        assertThat(client.calls).singleElement().satisfies(call -> {
            assertThat(call.threadId()).isNotEqualTo(importingThread);
            assertThat(call.inTransaction()).isFalse();
            assertThat(call.committedTickets()).isEqualTo(2);
            assertThat(call.texts())
                    .containsExactly(
                            "Login fails\nError 500\nBad token\nRestart", "Login fails\nError 500\nBad token\nRestart");
        });
        assertThat(jdbc.queryForList("SELECT vector_dims(embedding) FROM ticket_embedding", Integer.class))
                .containsExactly(1024, 1024);
        assertThat(jdbc.queryForList("SELECT model FROM ticket_embedding", String.class))
                .containsExactly("bge-m3", "bge-m3");
    }

    @Test
    void replacesOlderVector_AC_005_4() {
        confirms.confirm("pm", upload("EMBED-1", "EMBED-2", "EMBED-3"));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(embeddingCount()).isEqualTo(3));
        List<Long> ids = jdbc.queryForList("SELECT id FROM ticket ORDER BY id", Long.class);
        var before = jdbc.queryForMap(
                "SELECT ticket_embedding_id, created_at, updated_at FROM ticket_embedding WHERE ticket_id = ?",
                ids.getFirst());

        client.component = 2f;
        service.embed(List.of(ids.getFirst()));

        assertThat(embeddingCount()).isEqualTo(3);
        var after = jdbc.queryForMap(
                "SELECT ticket_embedding_id, created_at, updated_at FROM ticket_embedding WHERE ticket_id = ?",
                ids.getFirst());
        assertThat(after.get("ticket_embedding_id")).isEqualTo(before.get("ticket_embedding_id"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat((java.sql.Timestamp) after.get("updated_at")).isAfter((java.sql.Timestamp) before.get("updated_at"));
        assertThat(jdbc.queryForObject(
                        "SELECT (embedding::real[])[1] FROM ticket_embedding WHERE ticket_id = ?",
                        Float.class,
                        ids.getFirst()))
                .isEqualTo(2f);

        jdbc.update("DELETE FROM ticket_embedding");
        assertThat(repository.findPublishedWithoutEmbedding(2, new Long[0])).containsExactly(ids.get(0), ids.get(1));
        assertThat(repository.findPublishedWithoutEmbedding(2, new Long[] {ids.getFirst()}))
                .containsExactly(ids.get(1), ids.get(2));
        jdbc.update("UPDATE ticket SET status = 'UNPUBLISHED' WHERE id = ?", ids.get(1));
        assertThat(repository.findPublishedWithoutEmbedding(2, new Long[] {ids.getFirst()}))
                .containsExactly(ids.get(2));
    }

    @Test
    void importSucceedsWhenOllamaIsDown_AC_005_5() throws Exception {
        client.fail = true;
        assertThat(confirms.confirm("pm", upload("EMBED-1")).created()).isEqualTo(1);
        assertThat(client.called.await(10, TimeUnit.SECONDS)).isTrue();
        // Wait for the batch's failure handling before the next test resets the test client.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(service.skippedIds()).isEmpty();
            assertThat(embeddingCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM import_batch", String.class))
                    .isEqualTo("COMPLETED");
        });
        client.fail = false;
        service.embed(repository.findPublishedWithoutEmbedding(4, new Long[0]));
        assertThat(embeddingCount()).isEqualTo(1);
    }

    private long upload(String... ids) {
        StringBuilder csv = new StringBuilder("ticket_id,title,symptoms,root_cause,resolution_steps,scope\n");
        for (String id : ids) {
            csv.append(id).append(",Login fails,Error 500,Bad token,Restart,All users\n");
        }
        return imports.upload(
                        "pm",
                        1L,
                        new MockMultipartFile(
                                "file",
                                "tickets.csv",
                                "text/csv",
                                csv.toString().getBytes(StandardCharsets.UTF_8)))
                .previewId();
    }

    private int embeddingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM ticket_embedding", Integer.class);
    }
}
