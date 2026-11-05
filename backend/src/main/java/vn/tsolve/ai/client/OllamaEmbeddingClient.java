package vn.tsolve.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tsolve.ai.client.OllamaHttp.Operation;
import vn.tsolve.ai.exception.AiUnavailableException;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final OllamaHttp http;
    private final String model;

    OllamaEmbeddingClient(OllamaHttp http, @Value("${OLLAMA_EMBED_MODEL:bge-m3}") String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        return http.post(Operation.EMBED, model, Map.of("model", model, "input", texts), answer -> {
            JsonNode embeddings = answer.path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != texts.size()) {
                throw new AiUnavailableException();
            }
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (JsonNode values : embeddings) {
                if (!values.isArray() || values.size() != DIMENSIONS) {
                    throw new AiUnavailableException();
                }
                float[] vector = new float[DIMENSIONS];
                for (int i = 0; i < DIMENSIONS; i++) {
                    if (!values.get(i).isNumber()) {
                        throw new AiUnavailableException();
                    }
                    vector[i] = (float) values.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        });
    }
}
