package vn.tsolve.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withUserConfiguration(OllamaHttp.class, OllamaLlmClient.class, OllamaEmbeddingClient.class);

    @Test
    @DisplayName("AC-003.1 / edge 8 without OLLAMA_BASE_URL the context refuses to start and names the variable")
    void missingBaseUrlFails() {
        runner.withPropertyValues("OLLAMA_CHAT_MODEL=qwen").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("OLLAMA_BASE_URL"));
    }

    @Test
    @DisplayName("AC-003.1 without OLLAMA_CHAT_MODEL the context refuses to start and names the variable")
    void missingChatModelFails() {
        runner.withPropertyValues("OLLAMA_BASE_URL=http://127.0.0.1:9").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("OLLAMA_CHAT_MODEL"));
    }

    @Test
    @DisplayName("AC-003.7 / edge 7 the context starts while Ollama cannot be reached")
    void startsWithOllamaDown() {
        runner.withPropertyValues("OLLAMA_BASE_URL=http://127.0.0.1:9", "OLLAMA_CHAT_MODEL=qwen")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(LlmClient.class)
                        .hasSingleBean(EmbeddingClient.class));
    }
}
