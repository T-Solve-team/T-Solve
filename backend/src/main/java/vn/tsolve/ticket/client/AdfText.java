package vn.tsolve.ticket.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * Plain text of an Atlassian Document Format value (a Jira description or comment body), for the Import Ticket
 * Preview (AC-001.2). Text and mentions are kept, each block ends a line, nodes without text (media, rules) are
 * skipped. Formatting is dropped on purpose: the popup shows plain text.
 */
final class AdfText {

    private static final Set<String> BLOCKS = Set.of(
            "paragraph",
            "heading",
            "listItem",
            "codeBlock",
            "blockquote",
            "panel",
            "tableHeader",
            "tableCell",
            "decisionItem",
            "taskItem");

    private AdfText() {}

    /** The text, or null when the value is missing or holds no text. A plain string (old API) is kept as is. */
    static String of(JsonNode adf) {
        if (adf == null || adf.isNull() || adf.isMissingNode()) {
            return null;
        }
        if (adf.isTextual()) {
            return blankToNull(adf.asText());
        }
        StringBuilder text = new StringBuilder();
        append(adf, text);
        return blankToNull(text.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{2,}", "\n"));
    }

    private static void append(JsonNode node, StringBuilder text) {
        String type = node.path("type").asText();
        switch (type) {
            case "text" -> text.append(node.path("text").asText());
            case "mention", "emoji" -> text.append(
                    node.path("attrs").path("text").asText());
            case "hardBreak" -> text.append('\n');
            default -> {
                for (JsonNode child : node.path("content")) {
                    append(child, text);
                }
                if (BLOCKS.contains(type)) {
                    text.append('\n');
                }
            }
        }
    }

    private static String blankToNull(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
