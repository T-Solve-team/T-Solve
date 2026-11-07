package vn.tsolve.chatbot.answer.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

@Service
public class ChatbotAnswerServiceImpl implements ChatbotAnswerService {
    private static final Pattern MARKER = Pattern.compile("\\[([1-9]\\d?)\\]");
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

    @Autowired
    public ChatbotAnswerServiceImpl(
            EmbeddingClient embeddings,
            LlmClient llm,
            SolutionRetrieval solutions,
            WikiSources wiki,
            ChatbotAnswerRepository answers,
            PlatformTransactionManager transactionManager,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Value("${CHATBOT_MIN_SIMILARITY:0.7}") double minSimilarity,
            @Value("${CHATBOT_SOURCE_LIMIT:5}") int sourceLimit,
            @Value("${CHATBOT_ASK_TIMEOUT:60s}") String timeout) {
        this(
                embeddings,
                llm,
                solutions,
                wiki,
                answers,
                transactionManager,
                executor,
                minSimilarity,
                sourceLimit,
                DurationStyle.detectAndParse(timeout));
    }

    ChatbotAnswerServiceImpl(
            EmbeddingClient embeddings,
            LlmClient llm,
            SolutionRetrieval solutions,
            WikiSources wiki,
            ChatbotAnswerRepository answers,
            PlatformTransactionManager transactionManager,
            Executor executor,
            double minSimilarity,
            int sourceLimit,
            Duration timeout) {
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
    }

    @Override
    public ChatbotAnswerResponse ask(TSolveUser user, ChatbotQuestionRequest request) {
        long deadline = System.nanoTime() + timeoutNanos;
        String question = request.question().trim();
        // ponytail: Task 10 adds follow-up retrieval and prompt turns; history is accepted but not sent yet.
        float[] vector =
                callAi(() -> embeddings.embed(List.of(question)), deadline).getFirst();
        remaining(deadline);

        // Keep database access on the request thread so it retains the request's Workspace database.
        List<ChatbotSource> candidates = new ArrayList<>();
        solutions.nearest(vector, minSimilarity, sourceLimit).stream()
                .map(ChatbotSource::of)
                .forEach(candidates::add);
        remaining(deadline);
        wiki.nearest(user, vector, minSimilarity, sourceLimit).stream()
                .map(ChatbotSource::of)
                .forEach(candidates::add);
        candidates.sort(Comparator.comparingDouble(ChatbotSource::similarity)
                .reversed()
                .thenComparingInt(source -> source.citation() instanceof TicketSolutionCitation ? 0 : 1));
        List<ChatbotSource> kept = new ArrayList<>();
        for (int index = 0; index < Math.min(sourceLimit, candidates.size()); index++) {
            kept.add(candidates.get(index).numbered(index + 1));
        }
        remaining(deadline);

        if (kept.isEmpty()) {
            return save(user, question, Outcome.NO_SOURCE, "", List.of(), deadline);
        }
        var messages = AnswerPrompt.messages(question, kept);
        String text = callAi(() -> llm.chat(messages, null), deadline);
        remaining(deadline);
        if (!hasKeptMarker(text, kept.size())) {
            return save(user, question, Outcome.NO_SOURCE, "", List.of(), deadline);
        }
        // ponytail: Task 8 filters citations and removes unknown markers; Task 7 returns every kept source.
        return save(
                user,
                question,
                Outcome.ANSWERED,
                text,
                kept.stream().map(ChatbotSource::citation).toList(),
                deadline);
    }

    private ChatbotAnswerResponse save(
            TSolveUser user,
            String question,
            Outcome outcome,
            String text,
            List<ChatbotCitation> citations,
            long deadline) {
        return transaction.execute(status -> {
            remaining(deadline);
            ChatbotAnswer saved = answers.save(new ChatbotAnswer(user.id(), question, outcome.name(), text));
            remaining(deadline);
            // Flush before checking the deadline so a slow insert can still be rolled back.
            answers.flush();
            remaining(deadline);
            return new ChatbotAnswerResponse(saved.getId(), outcome, text, citations);
        });
    }

    private static boolean hasKeptMarker(String text, int count) {
        var matcher = MARKER.matcher(text);
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(1)) <= count) {
                return true;
            }
        }
        return false;
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
