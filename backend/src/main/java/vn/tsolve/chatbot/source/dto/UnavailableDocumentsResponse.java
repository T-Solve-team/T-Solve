package vn.tsolve.chatbot.source.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UnavailableDocumentsResponse(List<UUID> wikiDocumentIds) {
    public static UnavailableDocumentsResponse of(Set<UUID> requestedIds, Set<UUID> unavailableIds) {
        return new UnavailableDocumentsResponse(
                requestedIds.stream().filter(unavailableIds::contains).toList());
    }
}
