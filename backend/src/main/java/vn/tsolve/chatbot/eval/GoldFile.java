package vn.tsolve.chatbot.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GoldFile {
    private static final ObjectReader JSON =
            new ObjectMapper().readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private GoldFile() {}

    static List<GoldLine> parse(Path path) {
        List<String> input;
        try {
            input = new ArrayList<>(Files.readAllLines(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("gold file is missing or unreadable");
        }
        // A file saved by Windows PowerShell 5 or Notepad starts with a byte-order mark.
        if (!input.isEmpty() && input.getFirst().startsWith("﻿")) {
            input.set(0, input.getFirst().substring(1));
        }
        List<GoldLine> lines = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            if (!input.get(index).isBlank()) {
                lines.add(parseLine(index + 1, input.get(index)));
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("gold file is empty");
        }
        return List.copyOf(lines);
    }

    private static GoldLine parseLine(int line, String input) {
        JsonNode node;
        try {
            node = JSON.readValue(input);
        } catch (JsonProcessingException e) {
            throw invalid(line, "invalid JSON");
        }
        if (!node.isObject()) {
            throw invalid(line, "expected a JSON object");
        }
        String question = node.path("question").textValue();
        if (question == null || question.isBlank() || question.codePointCount(0, question.length()) > 1000) {
            throw invalid(line, "question must contain 1 to 1,000 characters and cannot be blank");
        }
        JsonNode expected = node.path("expected");
        if (!expected.isArray() || expected.isEmpty()) {
            throw invalid(line, "expected must be a non-empty array");
        }
        List<Source> sources = new ArrayList<>();
        expected.forEach(source -> sources.add(parseSource(line, source)));

        Map<String, String> groups = new HashMap<>();
        if (node.has("groups")) {
            JsonNode labels = node.get("groups");
            if (!labels.isObject()) {
                throw invalid(line, "groups must be an object");
            }
            labels.fields().forEachRemaining(field -> {
                if (!field.getValue().isTextual()) {
                    throw invalid(line, "group values must be strings");
                }
                groups.put(field.getKey(), field.getValue().textValue());
            });
        }
        return new GoldLine(line, question, List.copyOf(sources), Map.copyOf(groups));
    }

    private static Source parseSource(int line, JsonNode source) {
        Kind kind =
                switch (source.path("kind").asText()) {
                    case "TICKET" -> Kind.TICKET;
                    case "WIKI" -> Kind.WIKI;
                    default -> throw invalid(line, "expected source kind must be TICKET or WIKI");
                };
        String value = source.path("id").textValue();
        if (value == null) {
            throw invalid(line, "expected source id must be a UUID");
        }
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid(line, "expected source id must be a UUID");
        }
        if (!id.toString().equalsIgnoreCase(value)) {
            throw invalid(line, "expected source id must be a UUID");
        }
        return new Source(kind, id);
    }

    private static IllegalArgumentException invalid(int line, String reason) {
        return new IllegalArgumentException("line " + line + ": " + reason);
    }

    enum Kind {
        TICKET,
        WIKI
    }

    record Source(Kind kind, UUID id) {}

    record GoldLine(int line, String question, List<Source> expected, Map<String, String> groups) {}
}
