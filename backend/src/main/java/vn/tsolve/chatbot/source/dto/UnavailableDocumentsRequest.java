package vn.tsolve.chatbot.source.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.chatbot.exception.InvalidChatbotRequestException;

public record UnavailableDocumentsRequest(Set<UUID> wikiDocumentIds) {
    public static UnavailableDocumentsRequest of(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 50) throw new InvalidChatbotRequestException();
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) throw new InvalidChatbotRequestException();
            try {
                UUID id = UUID.fromString(value);
                if (!id.toString().equalsIgnoreCase(value)) throw new InvalidChatbotRequestException();
                ids.add(id);
            } catch (IllegalArgumentException invalid) {
                throw new InvalidChatbotRequestException();
            }
        }
        return new UnavailableDocumentsRequest(Collections.unmodifiableSet(ids));
    }
}
