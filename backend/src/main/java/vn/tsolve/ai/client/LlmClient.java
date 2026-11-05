package vn.tsolve.ai.client;

import java.util.List;
import java.util.Map;
import vn.tsolve.ai.exception.AiUnavailableException;

/** Chat completion on T-Solve's chat model. Call it outside a transaction. */
public interface LlmClient {

    /**
     * The text of the model's answer. With a {@code jsonSchema} (may be {@code null}) the answer is constrained to it
     * and returned only when it is a JSON object.
     *
     * @throws AiUnavailableException when no usable answer comes back
     */
    String chat(List<ChatMessage> messages, Map<String, Object> jsonSchema);
}
