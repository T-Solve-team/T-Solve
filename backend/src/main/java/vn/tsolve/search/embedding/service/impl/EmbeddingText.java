package vn.tsolve.search.embedding.service.impl;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import vn.tsolve.search.scope.entity.SearchTicket;

final class EmbeddingText {
    private EmbeddingText() {}

    static String of(SearchTicket ticket) {
        return Stream.of(
                        ticket.getTitle(),
                        ticket.getSymptoms(),
                        ticket.getRootCause(),
                        ticket.getResolutionSteps(),
                        ticket.getWorkaround())
                .filter(Objects::nonNull)
                .filter(section -> !section.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
