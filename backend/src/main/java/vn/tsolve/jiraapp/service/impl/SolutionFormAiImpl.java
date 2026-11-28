package vn.tsolve.jiraapp.service.impl;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import vn.tsolve.jiraapp.service.SolutionFormAi;
import vn.tsolve.ticket.dto.SolutionSections;

@Service
public class SolutionFormAiImpl implements SolutionFormAi {

    private static final Logger log = LoggerFactory.getLogger(SolutionFormAiImpl.class);

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
    private final Executor rewriteExecutor;
    private final Executor completionExecutor;
    private final Duration rewriteTimeout;
    private final BiConsumer<CompletableFuture<SolutionSections>, Duration> rewriteDeadline;

    // Its own pool, as for Jira App questions (BUG-183): a rewrite that waits must not hold a thread the web app's
    // own AI calls need. Not a bean, so Spring Boot still creates its applicationTaskExecutor.
    @Autowired
    public SolutionFormAiImpl(
            SolutionAiService ai,
            JiraAppJobCompletions completions,
            @Value("${TSOLVE_STUB_AI:}") String stub,
            @Value("${JIRA_APP_REWRITE_THREADS:4}") int rewriteThreads,
            @Value("${JIRA_APP_REWRITE_TIMEOUT:60s}") String rewriteTimeout) {
        this(
                ai,
                Mode.of(stub),
                JiraAppJobs.pool(rewriteThreads, "JIRA_APP_REWRITE_THREADS", "jira-app-rewrite-"),
                completions.executor(),
                DurationStyle.detectAndParse(rewriteTimeout),
                (future, limit) -> future.orTimeout(limit.toNanos(), TimeUnit.NANOSECONDS));
    }

    SolutionFormAiImpl(
            SolutionAiService ai,
            Mode mode,
            Executor rewriteExecutor,
            Executor completionExecutor,
            Duration rewriteTimeout,
            BiConsumer<CompletableFuture<SolutionSections>, Duration> rewriteDeadline) {
        this.ai = ai;
        this.mode = mode;
        this.rewriteExecutor = rewriteExecutor;
        this.completionExecutor = completionExecutor;
        this.rewriteTimeout = JiraAppJobs.jobTimeout(rewriteTimeout, "JIRA_APP_REWRITE_TIMEOUT");
        this.rewriteDeadline = rewriteDeadline;
    }

    @PreDestroy
    void stop() {
        if (rewriteExecutor instanceof ExecutorService pool) {
            pool.shutdownNow();
        }
    }

    @Override
    public void startRewrite(SolutionSections sections, BiConsumer<SolutionSections, Throwable> completion) {
        var future = JiraAppJobs.supply(
                () -> switch (mode) {
                    case AVAILABLE -> cannedRewrite(sections);
                    case UNAVAILABLE -> throw new AiRewriteUnavailableException();
                    case AI -> toForm(ai.rewrite(toAi(sections)));
                },
                rewriteExecutor);
        // Queue time counts; a timed-out rewrite is skipped if still queued, interrupted if running.
        rewriteDeadline.accept(future, rewriteTimeout);
        future.whenCompleteAsync(completion, completionExecutor);
    }

    @Override
    public void startDraft(
            String summary, String description, Supplier<List<String>> comments, Consumer<SolutionSections> onAiDraft) {
        if (mode != Mode.AI) {
            return;
        }
        var future = JiraAppJobs.supply(
                () -> {
                    SolutionDraft draft = ai.draft(new IssueContent(summary, description, comments.get()));
                    return draft.isAiDraft() ? toForm(draft.sections()) : null;
                },
                rewriteExecutor);
        // A late answer is dropped: the deadline completes the future first (AC-005.12).
        rewriteDeadline.accept(future, rewriteTimeout);
        future.whenCompleteAsync(
                (sections, error) -> {
                    if (error != null) {
                        // Only the type: a message could carry issue text (NF 3).
                        log.info(
                                "Issue AI draft not applied: error type {}",
                                error.getClass().getSimpleName());
                    } else if (sections != null) {
                        onAiDraft.accept(sections);
                    }
                },
                completionExecutor);
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
}
