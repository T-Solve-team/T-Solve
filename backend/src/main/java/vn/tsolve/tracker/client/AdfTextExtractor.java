package vn.tsolve.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plain text of an Atlassian Document Format (ADF) value, so the rest of T-Solve never parses Jira's format: the
 * text of each paragraph-like block on its own line; media, mentions and other non-text nodes are dropped.
 */
public final class AdfTextExtractor {

    private static final Set<String> LINE_BLOCKS =
            Set.of("paragraph", "heading", "codeBlock", "blockquote", "listItem", "tableCell", "tableHeader");

    private AdfTextExtractor() {}

    /** The text of {@code adf}; an empty string for {@code null}, JSON null or a document without text. */
    public static String toText(JsonNode adf) {
        if (adf == null || adf.isNull()) {
            return "";
        }
        if (adf.isTextual()) {
            return adf.asText().strip();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        collect(adf, lines, line);
        flush(lines, line);
        return String.join("\n", lines);
    }

    private static void collect(JsonNode node, List<String> lines, StringBuilder line) {
        String type = node.path("type").asText("");
        if ("text".equals(type)) {
            line.append(node.path("text").asText(""));
            return;
        }
        if ("hardBreak".equals(type)) {
            flush(lines, line);
            return;
        }
        boolean block = LINE_BLOCKS.contains(type);
        if (block) {
            flush(lines, line);
        }
        for (JsonNode child : node.path("content")) {
            collect(child, lines, line);
        }
        if (block) {
            flush(lines, line);
        }
    }

    private static void flush(List<String> lines, StringBuilder line) {
        String text = line.toString().strip();
        if (!text.isEmpty()) {
            lines.add(text);
        }
        line.setLength(0);
    }
}
