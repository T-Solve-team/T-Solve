package vn.tsolve.chatbot.source.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UnavailableDocumentsResponse(List<UUID> wikiDocumentIds) {
    /** Only ids that were asked about, in the order asked, whatever else the port returns. */
    public static UnavailableDocumentsResponse of(UnavailableDocumentsRequest request, Set<UUID> unavailable) {
        return new UnavailableDocumentsResponse(
                request.wikiDocumentIds().stream().filter(unavailable::contains).toList());
    }
}
