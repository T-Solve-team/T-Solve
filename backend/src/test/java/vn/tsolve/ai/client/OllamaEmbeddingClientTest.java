package vn.tsolve.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.ai.exception.AiUnavailableException;

@ExtendWith(OutputCaptureExtension.class)
class OllamaEmbeddingClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WireMockServer ollama = new WireMockServer(options().dynamicPort());
    private OllamaEmbeddingClient client;

    @BeforeEach
    void start() {
        ollama.start();
        OllamaHttp http = new OllamaHttp(
                ollama.baseUrl(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                2,
                Duration.ofSeconds(1));
        client = new OllamaEmbeddingClient(http, "bge-m3");
    }

    @AfterEach
    void stop() {
        ollama.stop();
    }

    /** One vector per first value, each of the given length, filled with that value. */
    private void answer(int length, double... firstValues) {
        ObjectNode body = JSON.createObjectNode();
        ArrayNode embeddings = body.putArray("embeddings");
        for (double value : firstValues) {
            ArrayNode vector = embeddings.addArray();
            for (int i = 0; i < length; i++) vector.add(value);
        }
        ollama.stubFor(post("/api/embed").willReturn(okJson(body.toString())));
    }

    @Test
    @DisplayName("AC-002.1 embeds every text in one call to /api/embed and keeps their order")
    void embedsInOneCallInOrder() {
        answer(1024, 0.25, -0.5);

        List<float[]> vectors = client.embed(List.of("máy in lỗi", "printer error"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).hasSize(1024).containsOnly(0.25f);
        assertThat(vectors.get(1)).hasSize(1024).containsOnly(-0.5f);
        ollama.verify(1, postRequestedFor(urlEqualTo("/api/embed")));
        ollama.verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"input\":[\"máy in lỗi\",\"printer error\"]}")));
    }

    @Test
    @DisplayName("AC-002.2 / edge 13 fewer vectors than texts is a failure")
    void fewerVectorsFails(CapturedOutput output) {
        answer(1024, 0.1);

        assertThatThrownBy(() -> client.embed(List.of("a", "b"))).isInstanceOf(AiUnavailableException.class);
        assertThat(output).contains("op=embed", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-002.2 / edge 12 a vector that is not 1024 long is a failure")
    void wrongLengthFails(CapturedOutput output) {
        answer(768, 0.1);

        assertThatThrownBy(() -> client.embed(List.of("a"))).isInstanceOf(AiUnavailableException.class);
        assertThat(output).contains("op=embed", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-002.2 / AC-003.6 non-numeric and null vector elements fail and log only a failure")
    void nonNumericVectorElementFails(CapturedOutput output) {
        for (JsonNode invalid : List.of(TextNode.valueOf("bad"), NullNode.getInstance())) {
            ObjectNode body = JSON.createObjectNode();
            ArrayNode vector = body.putArray("embeddings").addArray();
            vector.add(invalid);
            for (int i = 1; i < 1024; i++) vector.add(0.1);
            ollama.resetAll();
            ollama.stubFor(post("/api/embed").willReturn(okJson(body.toString())));

            assertThatThrownBy(() -> client.embed(List.of("a"))).isInstanceOf(AiUnavailableException.class);
        }
        assertThat(output).contains("op=embed", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-002.3 / edge 14 no texts returns no vectors without calling Ollama")
    void emptyListCallsNothing() {
        assertThat(client.embed(List.of())).isEmpty();
        ollama.verify(0, postRequestedFor(urlEqualTo("/api/embed")));
    }
}
