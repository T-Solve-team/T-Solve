package vn.tsolve.chatbot.issue.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.conversation.service.ChatConversationService;
import vn.tsolve.chatbot.exception.ChatbotJiraException;
import vn.tsolve.chatbot.exception.InvalidChatbotRequestException;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator.NewIssue;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueCreateResult;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotJiraIssueResponse;
import vn.tsolve.chatbot.issue.dto.IssueDepartment;
import vn.tsolve.chatbot.issue.repository.IssueTargetRepository;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

@Service
public class ChatbotIssueServiceImpl implements ChatbotIssueService {
    private static final Set<String> SEARCHED = Set.of(Outcome.ANSWERED.name(), Outcome.NO_SOURCE.name());
    private final ChatbotAnswerRepository answers;
    private final ChatConversationService conversations;
    private final IssueTargetRepository targets;
    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final long timeoutNanos;
    private final JiraServiceAccount accounts;
    private final JiraIssueCreator creator;

    @Autowired
    public ChatbotIssueServiceImpl(
            ChatbotAnswerRepository answers,
            ChatConversationService conversations,
            IssueTargetRepository targets,
            LlmClient llm,
            ObjectMapper mapper,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Value("${CHATBOT_ASK_TIMEOUT:60s}") String timeout,
            JiraServiceAccount accounts,
            JiraIssueCreator creator) {
        this(
                answers,
                conversations,
                targets,
                llm,
                mapper,
                executor,
                DurationStyle.detectAndParse(timeout),
                accounts,
                creator);
    }

    ChatbotIssueServiceImpl(
            ChatbotAnswerRepository answers,
            ChatConversationService conversations,
            IssueTargetRepository targets,
            LlmClient llm,
            ObjectMapper mapper,
            Executor executor,
            Duration timeout,
            JiraServiceAccount accounts,
            JiraIssueCreator creator) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid chatbot settings");
        }
        this.answers = answers;
        this.conversations = conversations;
        this.targets = targets;
        this.llm = llm;
        this.mapper = mapper;
        this.executor = executor;
        this.timeoutNanos = timeout.toNanos();
        this.accounts = accounts;
        this.creator = creator;
    }

    @Override
    public ChatbotIssueDraftResponse draft(TSolveUser user, String answerId) {
        long deadline = System.nanoTime() + timeoutNanos;
        UUID id;
        try {
            id = UUID.fromString(answerId);
        } catch (IllegalArgumentException invalid) {
            throw new NotFoundException("MSG04", Map.of());
        }
        var answer = answers.findByIdAndUserId(id, user.id())
                .filter(ChatbotIssueServiceImpl::searched)
                .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (targets.linkedJiraAccountId(user.id()).isEmpty()) {
            throw new BusinessRuleException("MSG146", Map.of());
        }
        // All database reads stay on the request thread, before the AI call, to retain the Workspace routing.
        var offered = targets.offeredDepartments();
        var history = conversations.turnsBefore(answer.getId());
        var departments = offered.stream()
                .map(target -> new IssueDepartment(target.departmentId(), target.name()))
                .toList();
        var empty = new ChatbotIssueDraftResponse("", "", null, departments);
        if (offered.isEmpty()) {
            return empty;
        }
        try {
            var messages = IssueDraftPrompt.messages(answer.getQuestion(), answer.getAnswerText(), history, offered);
            String reply = callAi(() -> llm.chat(messages, IssueDraftPrompt.SCHEMA), deadline);
            JsonNode root = reply == null
                    ? null
                    : mapper.readerFor(JsonNode.class)
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readValue(reply);
            remaining(deadline);
            if (root == null
                    || !root.isObject()
                    || !root.path("summary").isTextual()
                    || !root.path("description").isTextual()
                    || !root.path("department").isIntegralNumber()) {
                return empty;
            }
            JsonNode number = root.path("department");
            Long departmentId =
                    number.canConvertToInt() && number.intValue() >= 1 && number.intValue() <= offered.size()
                            ? offered.get(number.intValue() - 1).departmentId()
                            : null;
            return new ChatbotIssueDraftResponse(
                    cut(root.path("summary").textValue(), 255),
                    cut(root.path("description").textValue(), 10000),
                    departmentId,
                    departments);
        } catch (AiUnavailableException | JsonProcessingException failure) {
            return empty;
        }
    }

    @Override
    @Transactional
    public ChatbotIssueCreateResult create(TSolveUser user, String answerId, ChatbotIssueRequest request) {
        UUID id;
        try {
            id = UUID.fromString(answerId);
        } catch (IllegalArgumentException invalid) {
            throw new NotFoundException("MSG04", Map.of());
        }
        var answer = answers.findForUpdateByIdAndUserId(id, user.id())
                .filter(ChatbotIssueServiceImpl::searched)
                .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        if (answer.getJiraIssueKey() != null) {
            String site = account().siteUrl().replaceAll("/+$", "");
            return new ChatbotIssueCreateResult(
                    new ChatbotJiraIssueResponse(
                            answer.getJiraIssueKey(), site + "/browse/" + answer.getJiraIssueKey()),
                    false);
        }
        String reporter =
                targets.linkedJiraAccountId(user.id()).orElseThrow(() -> new BusinessRuleException("MSG146", Map.of()));
        var target = targets.offeredDepartments().stream()
                .filter(offered -> request.departmentId().equals(offered.departmentId()))
                .findFirst()
                .orElseThrow(InvalidChatbotRequestException::new);
        JiraAccount account = account();
        // ponytail: The answer row stays locked during the Jira call (10 s timeout); upgrade to a "creating" state if
        // this blocks.
        var created = creator.create(
                account, new NewIssue(target.jiraProjectKey(), request.summary(), request.description(), reporter));
        answer.recordJiraIssue(created.issueKey());
        return new ChatbotIssueCreateResult(new ChatbotJiraIssueResponse(created.issueKey(), created.issueUrl()), true);
    }

    // BUG-327: only a searched answer has an issue; a refused question must never reach the draft LLM.
    private static boolean searched(ChatbotAnswer answer) {
        return SEARCHED.contains(answer.getOutcome());
    }

    private JiraAccount account() {
        return accounts.current().orElseThrow(() -> new ChatbotJiraException(HttpStatus.BAD_GATEWAY, "MSG148"));
    }

    private <T> T callAi(Supplier<T> call, long deadline) {
        long budget = remaining(deadline);
        try {
            return CompletableFuture.supplyAsync(call, executor)
                    .orTimeout(budget, TimeUnit.NANOSECONDS)
                    .join();
        } catch (CompletionException | RejectedExecutionException failure) {
            // Discard provider causes, which may contain conversation or draft text.
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

    private static String cut(String text, int limit) {
        String trimmed = text.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), limit));
    }
}
