package vn.tsolve.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.dto.SolutionSections;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.service.SolutionAiService;

@Service
public class SolutionAiServiceImpl implements SolutionAiService {

    private static final int INPUT_LIMIT = 12_000;
    private static final int OUTPUT_LIMIT = 10_000;
    private static final String SYSTEM_PROMPT =
            """
            Draft a Solution from the issue. Write in the language of the issue, Vietnamese or English.
            Return only a JSON object with symptoms, rootCause, resolutionSteps, workaround,
            and scopeOfApplicability. Use plain text in each field. Do not invent facts.
            """;
    private static final String USER_PROMPT =
            """
            Summary: %s
            Description: %s
            Comments (oldest first):
            %s
            """;
    private static final String REWRITE_PROMPT =
            """
            Rewrite the filled sections of this Solution more clearly while keeping their meaning and the
            five-section template: Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability.
            Write in the language of the Solution, Vietnamese or English. Return only a JSON object with
            symptoms, rootCause, resolutionSteps, workaround, and scopeOfApplicability. Use plain text in
            each field. Leave every section not supplied empty.
            """;
    private static final Map<String, Object> JSON_SCHEMA = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                    "symptoms", Map.of("type", "string"),
                    "rootCause", Map.of("type", "string"),
                    "resolutionSteps", Map.of("type", "string"),
                    "workaround", Map.of("type", "string"),
                    "scopeOfApplicability", Map.of("type", "string")),
            "required",
            List.of("symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability"),
            "additionalProperties",
            false);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SolutionAiServiceImpl(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SolutionDraft draft(IssueContent issue) {
        SolutionDraft fallback = fallback(issue);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, SYSTEM_PROMPT),
                new ChatMessage(ChatMessage.Role.USER, userPrompt(issue)));
        try {
            // ponytail: Temperature is not sent until LlmClient takes options.
            String answer = llmClient.chat(messages, JSON_SCHEMA);
            SolutionSections sections = parseSections(answer);
            if (sections == null
                    || sections.symptoms().isBlank()
                    || sections.rootCause().isBlank()
                    || sections.resolutionSteps().isBlank()
                    || sections.scopeOfApplicability().isBlank()
                    || totalLength(sections) > OUTPUT_LIMIT) {
                return fallback;
            }
            return new SolutionDraft(sections, true);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return fallback;
        }
    }

    @Override
    public SolutionSections rewrite(SolutionSections solution) {
        String[] labels = {"Symptoms", "Root cause", "Resolution steps", "Workaround", "Scope of applicability"};
        String[] fields = {"symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability"};
        String[] originals = {
            solution.symptoms(),
            solution.rootCause(),
            solution.resolutionSteps(),
            solution.workaround(),
            solution.scopeOfApplicability()
        };
        List<String> filled = new ArrayList<>();
        for (int i = 0; i < originals.length; i++) {
            originals[i] = emptyIfNull(originals[i]).strip();
            if (!originals[i].isEmpty()) {
                filled.add(labels[i] + ": " + originals[i]);
            }
        }
        if (filled.isEmpty()) {
            return solution;
        }

        try {
            List<ChatMessage> messages = List.of(
                    new ChatMessage(ChatMessage.Role.SYSTEM, REWRITE_PROMPT),
                    new ChatMessage(ChatMessage.Role.USER, String.join("\n", filled)));
            JsonNode root = parseObject(llmClient.chat(messages, JSON_SCHEMA));
            if (root == null) {
                throw new AiRewriteUnavailableException();
            }
            String[] answer = new String[originals.length];
            for (int i = 0; i < originals.length; i++) {
                if (originals[i].isEmpty()) {
                    answer[i] = "";
                } else {
                    answer[i] = section(root, fields[i]);
                    if (answer[i] == null || answer[i].isEmpty()) {
                        throw new AiRewriteUnavailableException();
                    }
                }
            }
            SolutionSections rewritten = new SolutionSections(answer[0], answer[1], answer[2], answer[3], answer[4]);
            if (totalLength(rewritten) > OUTPUT_LIMIT) {
                throw new AiRewriteUnavailableException();
            }
            return rewritten;
        } catch (AiRewriteUnavailableException failure) {
            throw failure;
        } catch (RuntimeException | JsonProcessingException ignored) {
            throw new AiRewriteUnavailableException();
        }
    }

    private String userPrompt(IssueContent issue) {
        String description = emptyIfNull(issue.description());
        String selectedDescription = description.substring(0, Math.min(description.length(), INPUT_LIMIT));
        int remaining = INPUT_LIMIT - selectedDescription.length();
        List<String> comments = issue.comments() == null ? List.of() : issue.comments();
        List<String> selectedComments = new ArrayList<>();
        for (int i = comments.size() - 1; i >= 0; i--) {
            String comment = emptyIfNull(comments.get(i));
            if (comment.length() > remaining) {
                break;
            }
            selectedComments.add(comment);
            remaining -= comment.length();
        }
        Collections.reverse(selectedComments);
        return USER_PROMPT.formatted(
                emptyIfNull(issue.summary()), selectedDescription, String.join("\n", selectedComments));
    }

    private SolutionSections parseSections(String answer) throws JsonProcessingException {
        JsonNode root = parseObject(answer);
        if (root == null) {
            return null;
        }
        String symptoms = section(root, "symptoms");
        String rootCause = section(root, "rootCause");
        String resolutionSteps = section(root, "resolutionSteps");
        String workaround = section(root, "workaround");
        String scopeOfApplicability = section(root, "scopeOfApplicability");
        if (symptoms == null
                || rootCause == null
                || resolutionSteps == null
                || workaround == null
                || scopeOfApplicability == null) {
            return null;
        }
        return new SolutionSections(symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability);
    }

    private JsonNode parseObject(String answer) throws JsonProcessingException {
        JsonNode root = objectMapper
                .readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(answer);
        return root != null && root.isObject() ? root : null;
    }

    private String section(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value != null && value.isTextual() ? value.asText().strip() : null;
    }

    private int totalLength(SolutionSections sections) {
        return sections.symptoms().length()
                + sections.rootCause().length()
                + sections.resolutionSteps().length()
                + sections.workaround().length()
                + sections.scopeOfApplicability().length();
    }

    private SolutionDraft fallback(IssueContent issue) {
        List<String> comments = issue.comments();
        String lastComment =
                comments == null || comments.isEmpty() ? "" : emptyIfNull(comments.get(comments.size() - 1));
        return new SolutionDraft(
                new SolutionSections(emptyIfNull(issue.description()).strip(), "", lastComment.strip(), "", ""), false);
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
