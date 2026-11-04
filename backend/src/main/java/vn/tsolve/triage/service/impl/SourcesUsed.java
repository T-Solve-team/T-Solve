package vn.tsolve.triage.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * The readable items of {@code suggestion_result.sources_used}: {@code [{"type": "TICKET" | "WIKI", "id": …,
 * "title": …, "similarity": …}]} (spec: Interface Contracts). An item with an unknown type, no id, or a Wiki item
 * with no title is skipped, and unreadable JSON counts as no source, so one bad write never hides a result
 * (AC-004.4).
 */
record SourcesUsed(int ticketCount, List<WikiRef> wiki) {

    record WikiRef(String id, String title) {}

    static final SourcesUsed NONE = new SourcesUsed(0, List.of());

    int wikiCount() {
        return wiki.size();
    }

    static SourcesUsed parse(ObjectMapper json, String raw) {
        JsonNode items;
        try {
            items = raw == null ? null : json.readTree(raw);
        } catch (Exception unreadable) {
            return NONE;
        }
        if (items == null || !items.isArray()) {
            return NONE;
        }
        int tickets = 0;
        List<WikiRef> wiki = new ArrayList<>();
        for (JsonNode item : items) {
            String id = text(item.get("id"));
            if (id == null) {
                continue;
            }
            String type = text(item.get("type"));
            if ("TICKET".equals(type)) {
                tickets++;
            } else if ("WIKI".equals(type) && text(item.get("title")) != null) {
                wiki.add(new WikiRef(id, text(item.get("title"))));
            }
        }
        return new SourcesUsed(tickets, List.copyOf(wiki));
    }

    // An id may be written as a number or a string, whatever the team's id type ends up being.
    private static String text(JsonNode node) {
        if (node == null || !(node.isTextual() || node.isIntegralNumber())) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
