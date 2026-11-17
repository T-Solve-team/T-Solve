package vn.tsolve.chatbot.answer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.chatbot.answer.service.impl.QuestionClassifier.Category;
import vn.tsolve.chatbot.exception.ChatbotProjectNotActiveException;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;
import vn.tsolve.ticket.service.ProjectAccess;

@Service
public class ChatbotAnswerServiceImpl implements ChatbotAnswerService {
    private final EmbeddingClient embeddings;
    private final LlmClient llm;
    private final SolutionRetrieval solutions;
    private final WikiSources wiki;
    private final ChatbotAnswerRepository answers;
    private final TransactionTemplate transaction;
    private final Executor executor;
    private final double minSimilarity;
    private final int sourceLimit;
    private final long timeoutNanos;
    private final QuestionClassifier classifier;
    private final ProjectAccess projects;
    private final Clock clock;

    @Autowired
    public ChatbotAnswerServiceImpl(
            EmbeddingClient embeddings,
            LlmClient llm,
            SolutionRetrieval solutions,
            WikiSources wiki,
            ChatbotAnswerRepository answers,
            PlatformTransactionManager transactionManager,
            ObjectMapper json,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Value("${CHATBOT_MIN_SIMILARITY:0.7}") double minSimilarity,
            @Value("${CHATBOT_SOURCE_LIMIT:5}") int sourceLimit,
            @Value("${CHATBOT_ASK_TIMEOUT:60s}") String timeout,
            // Optional, so a context without the ticket module or a Clock bean still answers web questions (BUG-320).
            ObjectProvider<ProjectAccess> projects,
            ObjectProvider<Clock> clock) {
        this(
                embeddings,
                llm,
                solutions,
                wiki,
                answers,
                transactionManager,
                json,
                executor,
                minSimilarity,
                sourceLimit,
                DurationStyle.detectAndParse(timeout),
                projects.getIfAvailable(),
                clock.getIfAvailable(Clock::systemUTC));
    }

    ChatbotAnswerServiceImpl(
            EmbeddingClient embeddings,
            LlmClient llm,
            SolutionRetrieval solutions,
            WikiSources wiki,
            ChatbotAnswerRepository answers,
            PlatformTransactionManager transactionManager,
            ObjectMapper json,
            Executor executor,
            double minSimilarity,
            int sourceLimit,
            Duration timeout) {
        this(
                embeddings,
                llm,
                solutions,
                wiki,
                answers,
                transactionManager,
                json,
                executor,
                minSimilarity,
                sourceLimit,
                timeout,
                null,
                Clock.systemUTC());
    }

    ChatbotAnswerServiceImpl(
            EmbeddingClient embeddings,
            LlmClient llm,
            SolutionRetrieval solutions,
            WikiSources wiki,
            ChatbotAnswerRepository answers,
            PlatformTransactionManager transactionManager,
            ObjectMapper json,
            Executor executor,
            double minSimilarity,
            int sourceLimit,
            Duration timeout,
            ProjectAccess projects,
            Clock clock) {
        if (!Double.isFinite(minSimilarity)
                || minSimilarity < 0
                || minSimilarity > 1
                || sourceLimit < 1
                || timeout.isNegative()
                || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid chatbot settings");
        }
        this.embeddings = embeddings;
        this.llm = llm;
        this.solutions = solutions;
        this.wiki = wiki;
        this.answers = answers;
        this.transaction = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.minSimilarity = minSimilarity;
        this.sourceLimit = sourceLimit;
        this.timeoutNanos = timeout.toNanos();
        this.classifier = new QuestionClassifier(json);
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    public ChatbotAnswerResponse ask(TSolveUser user, ChatbotQuestionRequest request) {
        return answer(
                request,
                (vector, candidates) -> {
                    solutions.nearest(vector, minSimilarity, sourceLimit).stream()
                            .map(ChatbotSource::of)
                            .forEach(candidates::add);
                    return wiki.nearest(user, vector, minSimilarity, sourceLimit);
                },
                (question, outcome, text) -> new ChatbotAnswer(user.id(), question, outcome, text));
    }

    @Override
    public ChatbotAnswerResponse askForProject(
            UUID projectId, Optional<TSolveUser> reader, ChatbotQuestionRequest request) {
        // AC-012.4: refused before any AI call.
        if (projectId == null || projects == null || !projects.isActive(projectId)) {
            throw new ChatbotProjectNotActiveException();
        }
        return answer(
                request,
                (vector, candidates) -> {
                    solutions.nearestInProjectDepartment(projectId, vector, minSimilarity, sourceLimit).stream()
                            .map(ChatbotSource::of)
                            .forEach(candidates::add);
                    // AC-012.1: a linked asker reads what they may view on the web; anyone else Company-wide only.
                    return reader.isPresent()
                            ? wiki.nearest(reader.get(), vector, minSimilarity, sourceLimit)
                            : wiki.nearestCompanyWide(vector, minSimilarity, sourceLimit);
                },
                (question, outcome, text) -> ChatbotAnswer.forProject(projectId, question, outcome, text));
    }

    @Override
    @Transactional
    public void rateForProject(UUID projectId, UUID answerId, ChatbotRating rating) {
        if (projectId == null
                || answerId == null
                || rating == null
                || answers.updateProjectRating(answerId, projectId, rating, Instant.now(clock)) == 0) {
            throw new ChatbotAnswerNotFoundException();
        }
    }

    /** Adds the Solution sources to the list and returns the Wiki passages for one embedded question. */
    private interface Sources {
        List<WikiPassage> find(float[] vector, List<ChatbotSource> candidates);
    }

    private interface Row {
        ChatbotAnswer of(String question, String outcome, String text);
    }

    private ChatbotAnswerResponse answer(ChatbotQuestionRequest request, Sources sources, Row row) {
        long deadline = System.nanoTime() + timeoutNanos;
        String question = request.question().trim();
        if (QuestionClassifier.isGreeting(question)) {
            return save(row.of(question, Outcome.GREETING.name(), ""), Outcome.GREETING, List.of(), deadline);
        }
        var classifierMessages = ClassifierPrompt.messages(question, request.history());
        String reply = callAi(
                () -> {
                    try {
                        return llm.chat(classifierMessages, ClassifierPrompt.CLASSIFIER_SCHEMA);
                    } catch (AiUnavailableException failure) {
                        // Provider failures fail open here; callAi still enforces the shared deadline.
                        return null;
                    }
                },
                deadline);
        remaining(deadline);
        var classification = classifier.parse(reply);
        if (classification.category() != Category.QUESTION) {
            Outcome refused = Outcome.valueOf(classification.category().name());
            return save(row.of(question, refused.name(), ""), refused, List.of(), deadline);
        }
        // Use the private rewritten query only for retrieval; an unusable reply keeps the existing fallback.
        String fallbackText = request.history().isEmpty()
                ? question
                : request.history().getLast().question().trim() + "\n" + question;
        String retrievalText = classification.query().isEmpty() ? fallbackText : classification.query();
        float[] vector =
                callAi(() -> embeddings.embed(List.of(retrievalText)), deadline).getFirst();
        // Fail fast before the vector search if embedding has exhausted the deadline.
        remaining(deadline);

        // Keep database access on the request thread so it retains the request's Workspace database.
        List<ChatbotSource> candidates = new ArrayList<>();
        List<WikiPassage> passages = sources.find(vector, candidates);
        remaining(deadline);
        passages.stream().map(ChatbotSource::of).forEach(candidates::add);
        candidates.sort(Comparator.comparingDouble(ChatbotSource::similarity)
                .reversed()
                .thenComparingInt(source -> source.citation() instanceof TicketSolutionCitation ? 0 : 1));
        List<ChatbotSource> kept = new ArrayList<>();
        for (int index = 0; index < Math.min(sourceLimit, candidates.size()); index++) {
            kept.add(candidates.get(index).numbered(index + 1));
        }
        remaining(deadline);

        if (kept.isEmpty()) {
            return save(row.of(question, Outcome.NO_SOURCE.name(), ""), Outcome.NO_SOURCE, List.of(), deadline);
        }
        var messages = AnswerPrompt.messages(question, request.history(), kept);
        String text = callAi(() -> llm.chat(messages, null), deadline);
        remaining(deadline);
        var cited = Citations.of(text, kept);
        if (cited.citations().isEmpty()) {
            return save(row.of(question, Outcome.NO_SOURCE.name(), ""), Outcome.NO_SOURCE, List.of(), deadline);
        }
        return save(
                row.of(question, Outcome.ANSWERED.name(), cited.text()), Outcome.ANSWERED, cited.citations(), deadline);
    }

    private ChatbotAnswerResponse save(
            ChatbotAnswer answer, Outcome outcome, List<ChatbotCitation> citations, long deadline) {
        return transaction.execute(status -> {
            remaining(deadline);
            ChatbotAnswer saved = answers.save(answer);
            remaining(deadline);
            // Flush before checking the deadline so a slow insert can still be rolled back.
            answers.flush();
            // This check covers the insert and flush; COMMIT itself is not bounded by the ask deadline.
            remaining(deadline);
            return new ChatbotAnswerResponse(saved.getId(), outcome, answer.getAnswerText(), citations);
        });
    }

    private <T> T callAi(Supplier<T> call, long deadline) {
        long budget = remaining(deadline);
        try {
            return CompletableFuture.supplyAsync(call, executor)
                    .orTimeout(budget, TimeUnit.NANOSECONDS)
                    .join();
        } catch (CompletionException | RejectedExecutionException failure) {
            // No cause: a provider failure must never carry request or response content to the error handler.
            throw new AiUnavailableException();
        }
    }

    private static long remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new AiUnavailableException();
        }
        return remaining;
    }
}
