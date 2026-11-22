package vn.tsolve.jiraapp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * The plain text of an Atlassian Document Format value, with the same rules as the Jira App's {@code issueText}
 * ({@code watchDone.js}): paragraphs, headings, list items, code blocks and quotes on their own lines, trailing line
 * breaks removed. The Draft made at Done (AC-005.7) then equals the issue text the form shows when it opens in the issue
 * view (AC-002.1). Not {@code tracker}'s extractor: another module's {@code client/} is off limits (AGENTS §1).
 */
final class AdfText {

    private static final Set<String> BLOCKS = Set.of("paragraph", "heading", "listItem", "codeBlock", "blockquote");

    private AdfText() {}

    static String issueText(JsonNode description) {
        return text(description).replaceAll("\n+$", "");
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        String type = node.path("type").asText("");
        if ("text".equals(type)) {
            return node.path("text").asText("");
        }
        if ("hardBreak".equals(type)) {
            return "\n";
        }
        StringBuilder inner = new StringBuilder();
        for (JsonNode child : node.path("content")) {
            inner.append(text(child));
        }
        return BLOCKS.contains(type) ? inner.append('\n').toString() : inner.toString();
    }
}
