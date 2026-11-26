package vn.tsolve.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.chatbot.answer.service.ChatbotRetrieval;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

class RagEvalRunnerTest {
    private static final UUID WORKSPACE_ID = new UUID(0, 1);
    private static final UUID USER_ID = new UUID(0, 2);
    private static final UUID TICKET_ID = new UUID(0, 3);
    private static final UUID DOCUMENT_ID = new UUID(0, 4);
    private static final TSolveUser USER = new TSolveUser(USER_ID, "fake@example.invalid", "Test", Set.of());
    private static final String QUESTION = "  How do I fix error XYZ?  ";
    private static final float[] VECTOR = {1f, 0f};

    @TempDir
    Path directory;

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final SolutionRetrieval solutions = mock(SolutionRetrieval.class);
    private final WikiSources wiki = mock(WikiSources.class);
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final TenantContext tenants = mock(TenantContext.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void retrievesAsFirstWebQuestion_AC_015_1() throws Exception {
        writeGold(DOCUMENT_ID, "WIKI");
        bindWorkspace(Optional.of(USER));
        when(embeddings.embed(List.of(QUESTION.trim()))).thenReturn(List.of(VECTOR));
        when(solutions.nearest(VECTOR, 0, 10))
                .thenReturn(List.of(
                        new RetrievedSolution(TICKET_ID, "Symptoms", "Root cause", "Steps", null, "Scope", 0.45)));
        when(wiki.nearest(USER, VECTOR, 0, 10))
                .thenReturn(List.of(new WikiPassage(DOCUMENT_ID, "Title", "Passage", 0.45)));

        assertThat(run(settings())).isZero();

        var order = inOrder(tenants, accounts, embeddings, solutions, wiki);
        order.verify(tenants).runIn(eq(WORKSPACE_ID), ArgumentMatchers.<Supplier<?>>any());
        order.verify(accounts).findActive(USER_ID);
        order.verify(embeddings).embed(List.of(QUESTION.trim()));
        order.verify(solutions).nearest(VECTOR, 0, 10);
        order.verify(wiki).nearest(USER, VECTOR, 0, 10);
        String output = Files.readString(reportPath());
        var report = json.readTree(output);
        // AC-015.3: the default is the Chatbot's CHATBOT_MIN_SIMILARITY of AC-005.1 (0.5).
        assertThat(report.path("minSimilarity").doubleValue()).isEqualTo(0.5);
        assertThat(report.path("sourceLimit").intValue()).isEqualTo(5);
        assertThat(report.path("k").intValue()).isEqualTo(10);
        assertThat(report.at("/lines/0/rank").intValue()).isEqualTo(2);
        assertThat(report.at("/lines/0/similarity").doubleValue()).isEqualTo(0.45);
        assertThat(report.at("/overall/recall").doubleValue()).isZero();
        assertThat(output.lines().count()).isGreaterThan(1);
    }

    @Test
    void failsBeforeAiForUnknownUser_AC_015_5() throws Exception {
        writeGold(TICKET_ID, "TICKET");
        bindWorkspace(Optional.empty());

        assertThat(run(settings())).isEqualTo(1);
        verifyNoInteractions(embeddings, solutions, wiki, llm);
        assertThat(reportPath()).doesNotExist();
    }

    @Test
    void failsBeforeAiForUnknownWorkspace_AC_015_5() throws Exception {
        writeGold(TICKET_ID, "TICKET");
        when(tenants.runIn(eq(WORKSPACE_ID), ArgumentMatchers.<Supplier<?>>any()))
                .thenThrow(new IllegalArgumentException("the Workspace is not known"));

        assertThat(run(settings())).isEqualTo(1);
        verifyNoInteractions(accounts, embeddings, solutions, wiki, llm);
        assertThat(reportPath()).doesNotExist();
    }

    @Test
    void rejectsMissingGoldFile_AC_015_5() {
        assertThat(run(settings())).isEqualTo(1);
        verifyNoInteractions(tenants, accounts, embeddings, solutions, wiki, llm);
        assertThat(reportPath()).doesNotExist();
    }

    @Test
    void validatesEveryLineAndKBeforeRetrieval_AC_015_5() throws Exception {
        writeGold(TICKET_ID, "TICKET");
        assertThat(run(settings().withProperty("RAG_EVAL_K", "4"))).isEqualTo(1);

        String valid = Files.readString(goldPath());
        Files.writeString(goldPath(), valid + "\nprivate malformed content");
        assertThat(run(settings())).isEqualTo(1);
        verifyNoInteractions(tenants, accounts, embeddings, solutions, wiki, llm);
        assertThat(reportPath()).doesNotExist();
    }

    @Test
    void embeddingFailureWritesNoReport_AC_015_6() throws Exception {
        writeGold(TICKET_ID, "TICKET");
        String first = Files.readString(goldPath());
        Files.writeString(goldPath(), first + "\n" + first.replace(QUESTION, "Second question"));
        bindWorkspace(Optional.of(USER));
        when(embeddings.embed(List.of(QUESTION.trim()))).thenReturn(List.of(VECTOR));
        when(embeddings.embed(List.of("Second question")))
                .thenThrow(new IllegalStateException("private provider text"));

        Logger logger = (Logger) LoggerFactory.getLogger(RagEvalRunner.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            assertThat(run(settings())).isEqualTo(1);
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }

        assertThat(reportPath()).doesNotExist();
        assertThat(captured.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("rag-eval failed: line 3: embedding failed");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void callsNoChatModelAndStoresNothing_AC_015_7() throws Exception {
        writeGold(TICKET_ID, "TICKET");
        bindWorkspace(Optional.of(USER));
        when(embeddings.embed(List.of(QUESTION.trim()))).thenReturn(List.of(VECTOR));
        when(wiki.nearest(USER, VECTOR, 0, 2))
                .thenReturn(List.of(new WikiPassage(DOCUMENT_ID, "Private title", "Private passage", 0.9)));

        assertThat(run(settings()
                        .withProperty("RAG_EVAL_K", "2")
                        .withProperty("CHATBOT_MIN_SIMILARITY", "0.95")
                        .withProperty("CHATBOT_SOURCE_LIMIT", "1")))
                .isZero();

        var order = inOrder(tenants, accounts, embeddings, solutions, wiki);
        order.verify(tenants).runIn(eq(WORKSPACE_ID), ArgumentMatchers.<Supplier<?>>any());
        order.verify(accounts).findActive(USER_ID);
        order.verify(embeddings).embed(List.of(QUESTION.trim()));
        order.verify(solutions).nearest(VECTOR, 0, 2);
        order.verify(wiki).nearest(USER, VECTOR, 0, 2);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(llm);
        String output = Files.readString(reportPath());
        assertThat(output).doesNotContain(QUESTION.trim(), "Private title", "Private passage");
        var report = json.readTree(output);
        assertThat(report.at("/overall/noSourceRate").doubleValue()).isEqualTo(1);
        assertThat(report.at("/lines/0/rank").isNull()).isTrue();
        assertThat(report.at("/lines/0/similarity").isNull()).isTrue();

        assertThat(RagEvalRunner.class.getAnnotation(Profile.class).value()).containsExactly("rag-eval");
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-rag-eval.yml"));
        assertThat(yaml.getObject())
                .containsEntry("spring.main.web-application-type", "none")
                .containsEntry("spring.main.lazy-initialization", true)
                .containsEntry("spring.liquibase.enabled", false)
                .containsEntry("tsolve.registry.liquibase.enabled", false)
                .containsEntry("tsolve.worker.enabled", false)
                .containsEntry("tsolve.worker.web-jobs-enabled", false)
                .containsEntry("tsolve.worker.outbox-enabled", false)
                .containsEntry("TSOLVE_SESSION_SWEEP_CRON", "-")
                .containsEntry("CHATBOT_CONVERSATION_CLEANUP_CRON", "-")
                .containsEntry("tsolve.search.embedding.catch-up-enabled", false)
                .containsEntry("tsolve.ticket.preview-cleanup.enabled", false);
    }

    private void bindWorkspace(Optional<TSolveUser> user) {
        when(tenants.runIn(eq(WORKSPACE_ID), ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(accounts.findActive(USER_ID)).thenReturn(user);
    }

    private void writeGold(UUID id, String kind) throws Exception {
        Files.writeString(goldPath(), """
                {"question":"%s","expected":[{"kind":"%s","id":"%s"}]}
                """.formatted(QUESTION, kind, id));
    }

    private Path goldPath() {
        return directory.resolve("gold.jsonl");
    }

    private Path reportPath() {
        return directory.resolve("report.json");
    }

    private MockEnvironment settings() {
        return new MockEnvironment()
                .withProperty("RAG_EVAL_FILE", goldPath().toString())
                .withProperty("RAG_EVAL_REPORT", reportPath().toString())
                .withProperty("RAG_EVAL_WORKSPACE_ID", WORKSPACE_ID.toString())
                .withProperty("RAG_EVAL_USER_ID", USER_ID.toString());
    }

    private int run(MockEnvironment settings) {
        var exit = new AtomicInteger(-1);
        new RagEvalRunner(
                        embeddings,
                        solutions,
                        wiki,
                        accounts,
                        tenants,
                        new ChatbotRetrieval(),
                        json,
                        settings,
                        exit::set)
                .run(new DefaultApplicationArguments());
        return exit.get();
    }
}
