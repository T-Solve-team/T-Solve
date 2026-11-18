package vn.tsolve.jiraapp.service.impl;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJob;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility.Status;
import vn.tsolve.jiraapp.exception.JiraAppQuestionJobNotFoundException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.exception.JiraSiteNotConnectedException;
import vn.tsolve.jiraapp.service.JiraAppQuestionService;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.jiraapp.service.impl.RedisAskJobStore.Job;
import vn.tsolve.project.service.ProjectService;

/**
 * Ask T-Solve answers anyone in a Jira project of an Active T-Solve Project, from that Project's Department, without a
 * T-Solve sign-in (KAN-294). The site and the project come only from the verified FIT (AC-010.13).
 */
@Service
public class JiraAppQuestionServiceImpl implements JiraAppQuestionService {

    private static final String ASK_PREFIX = "jira-app:ask:";
    private static final Logger log = LoggerFactory.getLogger(JiraAppQuestionServiceImpl.class);

    private final JiraWorkspaceLookup workspaces;
    private final TenantScope tenantScope;
    private final ProjectService projects;
    private final JiraAskerLookup askers;
    private final ChatbotAnswerService answers;
    private final Executor executor;
    private final Executor completionExecutor;
    private final RedisAskJobStore jobs;
    private final Duration timeout;
    private final BiConsumer<CompletableFuture<ChatbotAnswerResponse>, Duration> deadline;

    // Its own pool, not the shared applicationTaskExecutor: a Jira question waiting there would hold a thread the
    // chatbot's own AI calls need, and enough of them would starve the web chatbot too (BUG-183). Not a bean, so
    // Spring Boot still creates its applicationTaskExecutor.
    @Autowired
    public JiraAppQuestionServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            ProjectService projects,
            JiraAskerLookup askers,
            ChatbotAnswerService answers,
            RedisAskJobStore jobs,
            JiraAppJobCompletions completions,
            @Value("${JIRA_APP_ASK_THREADS:8}") int threads,
            @Value("${JIRA_APP_ASK_TIMEOUT:60s}") String timeout) {
        this(
                workspaces,
                tenantScope,
                projects,
                askers,
                answers,
                jobs,
                askPool(threads),
                completions.executor(),
                DurationStyle.detectAndParse(timeout));
    }

    /** At most {@code threads} questions at once and as many waiting; any more answer MSG06 at once (BUG-183). */
    static ThreadPoolExecutor askPool(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("JIRA_APP_ASK_THREADS must be positive");
        }
        AtomicInteger number = new AtomicInteger();
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads),
                work -> {
                    Thread thread = new Thread(work, "jira-app-ask-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void stop() {
        if (executor instanceof ExecutorService pool) {
            pool.shutdownNow();
        }
    }

    JiraAppQuestionServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            ProjectService projects,
            JiraAskerLookup askers,
            ChatbotAnswerService answers,
            RedisAskJobStore jobs,
            Executor executor,
            Executor completionExecutor,
            Duration timeout) {
        this(
                workspaces,
                tenantScope,
                projects,
                askers,
                answers,
                jobs,
                executor,
                completionExecutor,
                timeout,
                (future, limit) -> future.orTimeout(limit.toNanos(), TimeUnit.NANOSECONDS));
    }

    JiraAppQuestionServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope tenantScope,
            ProjectService projects,
            JiraAskerLookup askers,
            ChatbotAnswerService answers,
            RedisAskJobStore jobs,
            Executor executor,
            Executor completionExecutor,
            Duration timeout,
            BiConsumer<CompletableFuture<ChatbotAnswerResponse>, Duration> deadline) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("JIRA_APP_ASK_TIMEOUT must be positive");
        }
        this.workspaces = workspaces;
        this.tenantScope = tenantScope;
        this.projects = projects;
        this.askers = askers;
        this.answers = answers;
        this.executor = executor;
        this.completionExecutor = completionExecutor;
        this.jobs = jobs;
        this.timeout = timeout;
        this.deadline = deadline;
    }

    @Override
    public JiraAskerEligibility eligibility(JiraAppPrincipal caller) {
        Optional<UUID> workspace = workspace(caller);
        if (workspace.isEmpty()) {
            return new JiraAskerEligibility(Status.SITE_NOT_CONNECTED);
        }
        return new JiraAskerEligibility(
                project(workspace.get(), caller).isPresent() ? Status.CONNECTED : Status.PROJECT_NOT_CONNECTED);
    }

    @Override
    public JiraAppQuestionJob start(JiraAppPrincipal caller, JiraAppQuestionRequest request) {
        UUID workspace = workspace(caller).orElseThrow(JiraSiteNotConnectedException::new);
        UUID project = project(workspace, caller).orElseThrow(JiraIssueAccessDeniedException::new);
        ChatbotQuestionRequest question = request.toChatbotQuestion();
        // AC-010.4: a Jira account linked to an active T-Solve user reads the Wiki that user may view.
        Optional<TSolveUser> reader =
                tenantScope.runIn(workspace, () -> askers.find(caller.cloudId(), caller.accountId()));
        UUID id = UUID.randomUUID();
        Job<ChatbotAnswerResponse> job = new Job<>(
                caller.cloudId(), caller.accountId(), caller.projectKey(), RedisAskJobStore.Status.PENDING, null);
        try {
            jobs.create(ASK_PREFIX, id, job);
        } catch (DataAccessException e) {
            logFailure(id, e);
            throw new AiUnavailableException();
        }
        try {
            // Workspace bindings are per thread; the worker binds it again (BUG-318).
            var future = CompletableFuture.supplyAsync(
                    () -> tenantScope.runIn(workspace, () -> answers.askForProject(project, reader, question)),
                    executor);
            deadline.accept(future, timeout);
            future.whenCompleteAsync(
                    (answer, error) -> {
                        if (error != null) {
                            logFailure(id, error);
                        }
                        try {
                            jobs.update(
                                    ASK_PREFIX,
                                    id,
                                    job.finish(
                                            error == null
                                                    ? RedisAskJobStore.Status.DONE
                                                    : RedisAskJobStore.Status.FAILED,
                                            error == null ? answer : null));
                        } catch (RuntimeException e) {
                            logFailure(id, e);
                        }
                    },
                    completionExecutor(id));
        } catch (RejectedExecutionException e) {
            try {
                jobs.delete(ASK_PREFIX, id);
            } catch (RuntimeException cleanupError) {
                log.warn(
                        "Jira App question job {}: error type {}",
                        id,
                        cleanupError.getClass().getSimpleName());
            }
            log.warn("Jira App question job {}: error type EXECUTOR_BUSY", id);
            throw new AiUnavailableException();
        }
        return new JiraAppQuestionJob(id);
    }

    @Override
    public JiraAppQuestionJobResult result(JiraAppPrincipal caller, UUID jobId) {
        Optional<Job<ChatbotAnswerResponse>> stored;
        try {
            stored = jobs.find(ASK_PREFIX, jobId, ChatbotAnswerResponse.class);
        } catch (DataAccessException e) {
            logFailure(jobId, e);
            throw new AiUnavailableException();
        }
        Job<ChatbotAnswerResponse> job = stored.filter(record -> caller != null
                        && Objects.equals(record.cloudId(), caller.cloudId())
                        && Objects.equals(record.accountId(), caller.accountId())
                        && Objects.equals(record.projectKey(), caller.projectKey()))
                .orElseThrow(JiraAppQuestionJobNotFoundException::new);
        return new JiraAppQuestionJobResult(
                JiraAppQuestionJobResult.Status.valueOf(job.status().name()),
                job.status() == RedisAskJobStore.Status.DONE ? job.payload() : null);
    }

    private Executor completionExecutor(UUID id) {
        return task -> {
            try {
                completionExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                // A saturated or stopped completion pool must not leave a completed job PENDING.
                logFailure(id, e);
                task.run();
            }
        };
    }

    private static void logFailure(UUID id, Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        log.warn("Jira App question job {}: error type {}", id, error.getClass().getSimpleName());
    }

    @Override
    public void rate(JiraAppPrincipal caller, UUID answerId, JiraAppRatingRequest request) {
        UUID workspace = workspace(caller).orElseThrow(JiraSiteNotConnectedException::new);
        UUID project = project(workspace, caller).orElseThrow(JiraIssueAccessDeniedException::new);
        tenantScope.runIn(workspace, () -> {
            answers.rateForProject(project, answerId, request.rating());
            return null;
        });
    }

    // No Workspace for a site that is not registered.
    private Optional<UUID> workspace(JiraAppPrincipal caller) {
        return caller == null || caller.cloudId() == null ? Optional.empty() : workspaces.workspaceOf(caller.cloudId());
    }

    // The Active Project of the FIT's Jira project, read in the site's Workspace; none without a project.
    // A FIT without a Jira user (anonymous access to the Jira project) gets no Project either (AC-010.11).
    private Optional<UUID> project(UUID workspace, JiraAppPrincipal caller) {
        String key = caller.projectKey();
        if (key == null
                || key.isBlank()
                || caller.accountId() == null
                || caller.accountId().isBlank()) {
            return Optional.empty();
        }
        return tenantScope.runIn(workspace, () -> projects.findActivePublicIdByJiraProjectKey(key));
    }
}
