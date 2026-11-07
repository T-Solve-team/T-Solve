package vn.tsolve.jiraapp.service.impl;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;
import vn.tsolve.jiraapp.exception.JiraAskerNotLinkedException;
import vn.tsolve.jiraapp.service.JiraAppQuestionService;
import vn.tsolve.jiraapp.service.JiraAskerLookup;

@Service
public class JiraAppQuestionServiceImpl implements JiraAppQuestionService {

    private static final Logger log = LoggerFactory.getLogger(JiraAppQuestionServiceImpl.class);

    private final JiraAskerLookup askers;
    private final ChatbotAnswerService answers;
    private final Executor executor;
    private final long timeoutNanos;

    @Autowired
    public JiraAppQuestionServiceImpl(
            JiraAskerLookup askers,
            ChatbotAnswerService answers,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Value("${JIRA_APP_ASK_TIMEOUT:18s}") String timeout) {
        this(askers, answers, executor, DurationStyle.detectAndParse(timeout));
    }

    JiraAppQuestionServiceImpl(
            JiraAskerLookup askers, ChatbotAnswerService answers, Executor executor, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("JIRA_APP_ASK_TIMEOUT must be positive");
        }
        this.askers = askers;
        this.answers = answers;
        this.executor = executor;
        this.timeoutNanos = timeout.toNanos();
    }

    @Override
    public JiraAskerEligibility eligibility(JiraAppPrincipal caller) {
        return new JiraAskerEligibility(asker(caller).isPresent());
    }

    @Override
    public ChatbotAnswerResponse ask(JiraAppPrincipal caller, JiraAppQuestionRequest request) {
        TSolveUser user = asker(caller).orElseThrow(JiraAskerNotLinkedException::new);
        ChatbotQuestionRequest question = request.toChatbotQuestion();
        // The chatbot allows itself CHATBOT_ASK_TIMEOUT (60 s); the Jira App must answer within Forge's 25 s, so the
        // call runs on a worker and is abandoned at the Jira App deadline (AC-010.8). It may still finish and store
        // its answer. No Workspace is bound to FIT calls yet; when one is, it must be bound on the worker too.
        try {
            return CompletableFuture.supplyAsync(() -> answers.ask(user, question), executor)
                    .orTimeout(timeoutNanos, TimeUnit.NANOSECONDS)
                    .join();
        } catch (RejectedExecutionException e) {
            log.warn("Jira App question not answered: error type EXECUTOR_BUSY");
            throw new AiUnavailableException();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("Jira App question not answered: error type TIMEOUT");
                throw new AiUnavailableException();
            }
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private Optional<TSolveUser> asker(JiraAppPrincipal caller) {
        if (caller == null || caller.accountId() == null || caller.accountId().isBlank()) {
            return Optional.empty();
        }
        return askers.find(caller.cloudId(), caller.accountId());
    }
}
