package vn.tsolve.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tsolve.ai.client.OllamaHttp.Operation;
import vn.tsolve.ai.exception.AiUnavailableException;

@Component
public class OllamaLlmClient implements LlmClient {

    private final OllamaHttp http;
    private final String model;
    private final ObjectMapper json;

    OllamaLlmClient(OllamaHttp http, @Value("${OLLAMA_CHAT_MODEL}") String model, ObjectMapper json) {
        this.http = http;
        this.model = OllamaHttp.required("OLLAMA_CHAT_MODEL", model);
        this.json = json;
    }

    @Override
    public String chat(List<ChatMessage> messages, Map<String, Object> jsonSchema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put(
                "messages",
                messages.stream()
                        .map(m -> Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", m.content()))
                        .toList());
        if (jsonSchema != null) {
            body.put("format", jsonSchema);
        }

        return http.post(Operation.CHAT, model, body, answer -> {
            String text = answer.path("message").path("content").asText("");
            if (text.isBlank() || (jsonSchema != null && !isJsonObject(text))) {
                throw new AiUnavailableException();
            }
            return text;
        });
    }

    private boolean isJsonObject(String text) {
        try {
            JsonNode node = json.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(text);
            return node != null && node.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
