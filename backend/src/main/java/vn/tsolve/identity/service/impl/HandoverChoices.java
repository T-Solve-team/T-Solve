package vn.tsolve.identity.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse.ProjectOption;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse.SpaceOption;

final class HandoverChoices {
    private HandoverChoices() {}

    record Choice(String id, String recipientId) {}

    static List<Choice> choices(JsonNode node, String idKey) {
        if (node == null) return List.of();
        if (!node.isArray()) throw malformed();
        List<Choice> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()
                    || !item.path(idKey).isTextual()
                    || !item.path("recipientId").isTextual()) throw malformed();
            result.add(new Choice(
                    item.get(idKey).textValue(), item.get("recipientId").textValue()));
        }
        return result;
    }

    static boolean matchesProjects(List<ProjectOption> options, List<Choice> choices) {
        return choices.size() == options.size()
                && distinct(choices)
                && options.stream().allMatch(option -> choices.stream()
                        .anyMatch(choice ->
                                option.project().projectId().toString().equals(choice.id())
                                        && option.recipients().stream()
                                                .anyMatch(user ->
                                                        user.id().toString().equals(choice.recipientId()))));
    }

    static boolean matchesSpaces(List<SpaceOption> options, List<Choice> choices) {
        return choices.size() == options.size()
                && distinct(choices)
                && options.stream().allMatch(option -> choices.stream()
                        .anyMatch(choice -> option.space().spaceId().toString().equals(choice.id())
                                && option.recipients().stream()
                                        .anyMatch(user -> user.id().toString().equals(choice.recipientId()))));
    }

    static boolean distinct(List<Choice> choices) {
        return choices.stream().map(Choice::id).distinct().count() == choices.size();
    }

    static UUID uuid(String value) {
        if (value == null
                || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            return null;
        return UUID.fromString(value);
    }

    private static FieldMessageCodeException malformed() {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG06", Map.of(), null);
    }
}
