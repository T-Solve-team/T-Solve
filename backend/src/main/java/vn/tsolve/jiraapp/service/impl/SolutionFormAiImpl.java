package vn.tsolve.jiraapp.service.impl;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.service.SolutionAiService;
import vn.tsolve.jiraapp.dto.SolutionFormDraft;
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.ticket.dto.SolutionSections;

@Service
public class SolutionFormAiImpl implements SolutionFormAi {

    private static final Logger log = LoggerFactory.getLogger(SolutionFormAiImpl.class);

    /** The five sections together may hold this many characters (AC-002.4). */
    static final int MAX_SOLUTION_LENGTH = 10_000;

    /** {@code TSOLVE_STUB_AI}: empty = the `ai` module; these two serve demos without the AI server. */
    enum Mode {
        AI,
        AVAILABLE,
        UNAVAILABLE;

        static Mode of(String setting) {
            String value = setting == null ? "" : setting.strip().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "" -> AI;
                case "available" -> AVAILABLE;
                case "unavailable" -> UNAVAILABLE;
                default -> throw new IllegalArgumentException("TSOLVE_STUB_AI must be empty, available or unavailable");
            };
        }
    }

    private final SolutionAiService ai;
    private final Mode mode;
    private final Executor executor;
    private final long timeoutNanos;

    // Its own pool, as for Jira App questions (BUG-183): an AI call that waits must not hold a thread the web app's
    // own AI calls need. Not a bean, so Spring Boot still creates its applicationTaskExecutor.
    @Autowired
    public SolutionFormAiImpl(
            SolutionAiService ai,
            @Value("${TSOLVE_STUB_AI:}") String stub,
            @Value("${JIRA_APP_AI_THREADS:4}") int threads,
            @Value("${JIRA_APP_AI_TIMEOUT:18s}") String timeout) {
        this(ai, Mode.of(stub), pool(threads), DurationStyle.detectAndParse(timeout));
    }

    SolutionFormAiImpl(SolutionAiService ai, Mode mode, Executor executor, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("JIRA_APP_AI_TIMEOUT must be positive");
        }
        this.ai = ai;
        this.mode = mode;
        this.executor = executor;
        this.timeoutNanos = timeout.toNanos();
    }

    @PreDestroy
    void stop() {
        if (executor instanceof ExecutorService pool) {
            pool.shutdownNow();
        }
    }

    @Override
    public SolutionFormDraft draft(String summary, String description) {
        SolutionFormDraft fromIssue = new SolutionFormDraft(fromIssue(summary, description), false);
        if (mode == Mode.UNAVAILABLE) {
            return fromIssue;
        }
        if (mode == Mode.AVAILABLE) {
            return new SolutionFormDraft(cannedDraft(summary), true);
        }
        try {
            SolutionDraft draft = withinDeadline(() -> ai.draft(new IssueContent(summary, description, List.of())));
            // The ai module falls back by itself; its fallback is not the one of AC-002.2, so ours is used instead.
            return draft.isAiDraft() ? new SolutionFormDraft(toForm(draft.sections()), true) : fromIssue;
        } catch (RuntimeException e) {
            log.warn("Solution Form AI draft not used: error type {}", errorType(e));
            return fromIssue;
        }
    }

    @Override
    public SolutionSections rewrite(SolutionSections sections) {
        if (mode == Mode.UNAVAILABLE) {
            throw new AiRewriteUnavailableException();
        }
        if (mode == Mode.AVAILABLE) {
            return cannedRewrite(sections);
        }
        try {
            return toForm(withinDeadline(() -> ai.rewrite(toAi(sections))));
        } catch (RuntimeException e) {
            log.warn("Solution Form AI rewrite failed: error type {}", errorType(e));
            throw new AiRewriteUnavailableException();
        }
    }

    private <T> T withinDeadline(Supplier<T> call) {
        try {
            return CompletableFuture.supplyAsync(call, executor)
                    .orTimeout(timeoutNanos, TimeUnit.NANOSECONDS)
                    .join();
        } catch (CompletionException e) {
            // No cause kept: the AI's failure must not carry issue or Solution text into a log (NF 3).
            throw e.getCause() instanceof RuntimeException cause && !(cause instanceof CompletionException)
                    ? new AiCallFailed(cause.getClass().getSimpleName())
                    : new AiCallFailed("Timeout");
        } catch (RejectedExecutionException e) {
            throw new AiCallFailed("Busy");
        }
    }

    private static String errorType(RuntimeException e) {
        return e instanceof AiCallFailed ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** AC-002.2: the summary and description in Symptoms, the other sections empty, cut to the Solution limit. */
    static SolutionSections fromIssue(String summary, String description) {
        String text = join(summary, description);
        if (text.codePointCount(0, text.length()) > MAX_SOLUTION_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_SOLUTION_LENGTH));
        }
        return new SolutionSections(text, "", "", "", "");
    }

    private static String join(String summary, String description) {
        String first = summary == null ? "" : summary.strip();
        String second = description == null ? "" : description.strip();
        if (first.isEmpty() || second.isEmpty()) {
            return first + second;
        }
        return first + "\n\n" + second;
    }

    private static SolutionSections cannedDraft(String summary) {
        String issue = summary == null || summary.isBlank() ? "The reported problem" : summary.strip();
        return new SolutionSections(
                issue + ".",
                "Stand-in AI draft (TSOLVE_STUB_AI=available): write the real root cause.",
                "1. Reproduce the problem.\n2. Apply the fix.\n3. Confirm with the reporter.",
                "",
                "The Project's users who report the same problem.");
    }

    private static SolutionSections cannedRewrite(SolutionSections sections) {
        return new SolutionSections(
                tidy(sections.symptoms()),
                tidy(sections.rootCause()),
                tidy(sections.resolutionSteps()),
                tidy(sections.workaround()),
                tidy(sections.scopeOfApplicability()));
    }

    // Visible but harmless: the stand-in only trims and ends each filled section with a full stop.
    private static String tidy(String text) {
        String value = text.strip();
        return value.isEmpty() || value.endsWith(".") ? value : value + ".";
    }

    private static SolutionSections toForm(vn.tsolve.ai.dto.SolutionSections sections) {
        return new SolutionSections(
                sections.symptoms(),
                sections.rootCause(),
                sections.resolutionSteps(),
                sections.workaround(),
                sections.scopeOfApplicability());
    }

    private static vn.tsolve.ai.dto.SolutionSections toAi(SolutionSections sections) {
        return new vn.tsolve.ai.dto.SolutionSections(
                sections.symptoms(),
                sections.rootCause(),
                sections.resolutionSteps(),
                sections.workaround(),
                sections.scopeOfApplicability());
    }

    private static ThreadPoolExecutor pool(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("JIRA_APP_AI_THREADS must be positive");
        }
        AtomicInteger number = new AtomicInteger();
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads),
                work -> {
                    Thread thread = new Thread(work, "jira-app-ai-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** A failed AI call with only its error type: no message, cause or stack that could carry text. */
    private static final class AiCallFailed extends RuntimeException {
        AiCallFailed(String type) {
            super(type, null, false, false);
        }
    }
}
