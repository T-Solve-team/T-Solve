package vn.tsolve.chatbot.source.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.UUID;
import vn.tsolve.chatbot.exception.InvalidChatbotRequestException;

/** The cited Wiki document ids to check: 1 to 50 distinct canonical UUIDs, in the order first given. */
public record UnavailableDocumentsRequest(SequencedSet<UUID> wikiDocumentIds) {
    public static final int MAX_IDS = 50;

    public static UnavailableDocumentsRequest of(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new InvalidChatbotRequestException();
        }
        var ids = new LinkedHashSet<UUID>();
        for (String value : values) {
            ids.add(uuid(value));
        }
        if (ids.size() > MAX_IDS) {
            throw new InvalidChatbotRequestException();
        }
        return new UnavailableDocumentsRequest(Collections.unmodifiableSequencedSet(ids));
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            // UUID.fromString also accepts short forms such as 1-1-1-1-1.
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new InvalidChatbotRequestException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidChatbotRequestException();
        }
    }
}
