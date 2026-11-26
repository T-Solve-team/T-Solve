package vn.tsolve.chatbot.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.chatbot.answer.service.ChatbotRetrieval;
import vn.tsolve.chatbot.answer.service.impl.ChatbotSource;
import vn.tsolve.chatbot.eval.GoldFile.GoldLine;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

@Component
@Profile("rag-eval")
public class RagEvalRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(RagEvalRunner.class);

    private final EmbeddingClient embeddings;
    private final SolutionRetrieval solutions;
    private final WikiSources wiki;
    private final AccountLookupService accounts;
    private final TenantContext tenants;
    private final ChatbotRetrieval retrieval;
    private final ObjectMapper json;
    private final Environment settings;
    private final IntConsumer exit;

    @Autowired
    public RagEvalRunner(
            EmbeddingClient embeddings,
            SolutionRetrieval solutions,
            WikiSources wiki,
            AccountLookupService accounts,
            TenantContext tenants,
            ChatbotRetrieval retrieval,
            ObjectMapper json,
            Environment settings,
            ConfigurableApplicationContext context) {
        this(
                embeddings,
                solutions,
                wiki,
                accounts,
                tenants,
                retrieval,
                json,
                settings,
                code -> System.exit(SpringApplication.exit(context, () -> code)));
    }

    RagEvalRunner(
            EmbeddingClient embeddings,
            SolutionRetrieval solutions,
            WikiSources wiki,
            AccountLookupService accounts,
            TenantContext tenants,
            ChatbotRetrieval retrieval,
            ObjectMapper json,
            Environment settings,
            IntConsumer exit) {
        this.embeddings = embeddings;
        this.solutions = solutions;
        this.wiki = wiki;
        this.accounts = accounts;
        this.tenants = tenants;
        this.retrieval = retrieval;
        this.json = json;
        this.settings = settings;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        exit.accept(evaluate());
    }

    private int evaluate() {
        try {
            Settings config = readSettings();
            List<GoldLine> lines = readGold(config.gold());
            List<List<ChatbotSource>> results = retrieve(config, lines);
            writeReport(
                    config.report(),
                    RagEvalReport.of(lines, results, config.minSimilarity(), config.sourceLimit(), config.k()));
            return 0;
        } catch (Failure e) {
            LOG.error("rag-eval failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("rag-eval failed: unexpected evaluation error");
        }
        return 1;
    }

    private List<List<ChatbotSource>> retrieve(Settings config, List<GoldLine> lines) {
        try {
            return tenants.runIn(config.workspace(), () -> {
                var user = accounts.findActive(config.user())
                        .orElseThrow(() -> new Failure("user is unknown or inactive"));
                List<List<ChatbotSource>> results = new ArrayList<>(lines.size());
                for (GoldLine line : lines) {
                    float[] vector = embed(line);
                    try {
                        var tickets = solutions.nearest(vector, 0, config.k());
                        var passages = wiki.nearest(user, vector, 0, config.k());
                        results.add(retrieval.rank(tickets, passages, config.k()));
                    } catch (RuntimeException e) {
                        throw new Failure("line " + line.line() + ": source retrieval failed");
                    }
                }
                return results;
            });
        } catch (IllegalArgumentException e) {
            // TenantContext throws this for an unknown Workspace before calling the supplier.
            throw new Failure("Workspace is unknown");
        }
    }

    private float[] embed(GoldLine line) {
        try {
            return embeddings.embed(List.of(line.question().trim())).getFirst();
        } catch (RuntimeException e) {
            throw new Failure("line " + line.line() + ": embedding failed");
        }
    }

    private static List<GoldLine> readGold(Path path) {
        try {
            return GoldFile.parse(path);
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage());
        }
    }

    private void writeReport(Path path, RagEvalReport report) {
        Path destination = path.toAbsolutePath();
        try {
            Path temporary = Files.createTempFile(destination.getParent(), ".rag-eval-", ".json");
            try {
                json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), report);
                try {
                    Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new Failure("could not write the report");
        }
    }

    private Settings readSettings() {
        int sourceLimit = setting("CHATBOT_SOURCE_LIMIT", Integer.class, 5);
        int k = setting("RAG_EVAL_K", Integer.class, 10);
        double minSimilarity = setting("CHATBOT_MIN_SIMILARITY", Double.class, 0.5);
        if (sourceLimit < 1) {
            throw new Failure("CHATBOT_SOURCE_LIMIT must be positive");
        }
        if (k < sourceLimit) {
            throw new Failure("RAG_EVAL_K must be at least CHATBOT_SOURCE_LIMIT");
        }
        if (!Double.isFinite(minSimilarity) || minSimilarity < 0 || minSimilarity > 1) {
            throw new Failure("CHATBOT_MIN_SIMILARITY must be between 0 and 1");
        }
        return new Settings(
                path("RAG_EVAL_FILE"),
                path("RAG_EVAL_REPORT"),
                setting("RAG_EVAL_WORKSPACE_ID", UUID.class, null),
                setting("RAG_EVAL_USER_ID", UUID.class, null),
                k,
                sourceLimit,
                minSimilarity);
    }

    private Path path(String name) {
        String value = setting(name, String.class, null);
        try {
            if (!value.isBlank()) {
                return Path.of(value);
            }
        } catch (IllegalArgumentException e) {
            throw new Failure(name + " must be a valid path");
        }
        throw new Failure(name + " must be a valid path");
    }

    private <T> T setting(String name, Class<T> type, T fallback) {
        try {
            return fallback == null
                    ? settings.getRequiredProperty(name, type)
                    : settings.getProperty(name, type, fallback);
        } catch (RuntimeException e) {
            throw new Failure(name + " is missing or invalid");
        }
    }

    private record Settings(
            Path gold, Path report, UUID workspace, UUID user, int k, int sourceLimit, double minSimilarity) {}

    /** Only these errors contain messages intended for the operator. */
    private static final class Failure extends RuntimeException {
        Failure(String reason) {
            super(reason);
        }
    }
}
