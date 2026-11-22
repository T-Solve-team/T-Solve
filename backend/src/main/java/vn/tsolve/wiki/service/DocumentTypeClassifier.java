package vn.tsolve.wiki.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.wiki.entity.WikiDocumentType;

@Component
public class DocumentTypeClassifier {
    private static final Map<String, Object> SCHEMA = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                    "documentType",
                    Map.of(
                            "type",
                            "string",
                            "enum",
                            Arrays.stream(WikiDocumentType.values())
                                    .map(Enum::name)
                                    .toList())),
            "required",
            List.of("documentType"),
            "additionalProperties",
            false);
    private static final int MAX_SAMPLE_LENGTH = 4000;

    private final LlmClient llm;
    private final ObjectReader reader;
    private final ChatMessage instructions;
    private final Duration timeout;
    private final Duration totalTimeout;

    public DocumentTypeClassifier(
            LlmClient llm,
            ObjectMapper json,
            @Value("${tsolve.wiki.classify.timeout:20s}") String timeout,
            @Value("${tsolve.wiki.classify.total-timeout:60s}") String totalTimeout) {
        this.llm = llm;
        this.reader = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.timeout = DurationStyle.detectAndParse(timeout);
        this.totalTimeout = DurationStyle.detectAndParse(totalTimeout);
        try {
            this.instructions = new ChatMessage(
                    ChatMessage.Role.SYSTEM,
                    new ClassPathResource("prompts/wiki-document-type.txt").getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load the Wiki document type prompt", failure);
        }
    }

    /** The end of one extract request's classification budget, from now (AC-007.3, BUG-356). */
    public Instant deadline() {
        return Instant.now().plus(totalTimeout);
    }

    public WikiDocumentType classify(String title, String text) {
        return classify(title, text, Instant.now().plus(timeout));
    }

    /**
     * Blocks the caller until the per-file timeout or the deadline, whichever comes first; a late call is interrupted
     * so it frees its Ollama slot (AC-007.3, BUG-355, BUG-356).
     */
    public WikiDocumentType classify(String title, String text, Instant deadline) {
        if (text.isBlank()) return WikiDocumentType.OTHER;
        long waitMillis =
                Math.min(timeout.toMillis(), Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis()));
        if (waitMillis == 0) return WikiDocumentType.OTHER;
        String sample = text.substring(0, Math.min(MAX_SAMPLE_LENGTH, text.length()));
        var input = new ChatMessage(ChatMessage.Role.USER, "Title: " + title + "\n\nExtracted text:\n" + sample);
        var call = new FutureTask<>(() -> classify(input));
        // Socket reads on a virtual thread stop on interrupt, so cancel(true) ends the HTTP call and its slot.
        Thread.startVirtualThread(call);
        try {
            return call.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return WikiDocumentType.OTHER;
        } catch (ExecutionException | TimeoutException failed) {
            return WikiDocumentType.OTHER;
        } finally {
            call.cancel(true);
        }
    }

    private WikiDocumentType classify(ChatMessage input) {
        String answer = llm.chat(List.of(instructions, input), SCHEMA);
        if (answer == null) return WikiDocumentType.OTHER;
        try {
            JsonNode result = reader.readValue(answer);
            JsonNode type = result.path("documentType");
            if (!result.isObject() || result.size() != 1 || !type.isTextual()) return WikiDocumentType.OTHER;
            return WikiDocumentType.valueOf(type.textValue().toUpperCase(Locale.ROOT));
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            return WikiDocumentType.OTHER;
        }
    }
}
