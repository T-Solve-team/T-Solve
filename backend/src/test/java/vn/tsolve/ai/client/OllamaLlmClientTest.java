package vn.tsolve.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.ai.client.ChatMessage.Role;
import vn.tsolve.ai.exception.AiUnavailableException;

@ExtendWith(OutputCaptureExtension.class)
class OllamaLlmClientTest {

    private final WireMockServer ollama = new WireMockServer(options().dynamicPort());
    private OllamaLlmClient client;

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
        client = new OllamaLlmClient(http, "qwen-test", new ObjectMapper());
    }

    @AfterEach
    void stop() {
        ollama.stop();
    }

    private void answer(String content) {
        String body = new ObjectMapper()
                .createObjectNode()
                .set(
                        "message",
                        new ObjectMapper()
                                .createObjectNode()
                                .put("role", "assistant")
                                .put("content", content))
                .toString();
        ollama.stubFor(post("/api/chat").willReturn(okJson(body)));
    }

    @Test
    @DisplayName("AC-001.1 sends the messages to /api/chat with the chat model, not streamed, and returns the answer")
    void sendsMessagesAndReturnsAnswer() {
        answer("Xin chào");

        String text = client.chat(
                List.of(new ChatMessage(Role.SYSTEM, "Be short"), new ChatMessage(Role.USER, "Hello")), null);

        assertThat(text).isEqualTo("Xin chào");
        ollama.verify(
                postRequestedFor(urlEqualTo("/api/chat"))
                        .withRequestBody(
                                equalToJson(
                                        """
                        {"model":"qwen-test","stream":false,
                         "messages":[{"role":"system","content":"Be short"},{"role":"user","content":"Hello"}]}
                        """)));
    }

    @Test
    @DisplayName("AC-001.2 sends the JSON schema as format and returns a JSON object answer")
    void sendsSchemaAsFormat() {
        answer("{\"symptoms\":\"Login fails\"}");
        Map<String, Object> schema =
                Map.of("type", "object", "properties", Map.of("symptoms", Map.of("type", "string")));

        String text = client.chat(List.of(new ChatMessage(Role.USER, "Draft")), schema);

        assertThat(text).isEqualTo("{\"symptoms\":\"Login fails\"}");
        ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(equalToJson(
                        """
                        {"format":{"type":"object","properties":{"symptoms":{"type":"string"}}}}
                        """,
                        true,
                        true)));
    }

    @Test
    @DisplayName("AC-001.3 a blank answer is a failure")
    void blankAnswerFails(CapturedOutput output) {
        answer("   ");

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(Role.USER, "Hi")), null))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(output).contains("op=chat", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-001.3 / edge 10 a JSON answer cut short does not parse and is a failure")
    void truncatedJsonFails(CapturedOutput output) {
        answer("{\"symptoms\":\"Login fa");

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(Role.USER, "Hi")), Map.of("type", "object")))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(output).contains("op=chat", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-001.3 a JSON answer that is not an object is a failure")
    void jsonArrayFails() {
        answer("[\"a\"]");

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(Role.USER, "Hi")), Map.of("type", "object")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    @DisplayName("AC-001.3 / AC-003.6 trailing text after a JSON object fails and logs only a failure")
    void trailingJsonTextFails(CapturedOutput output) {
        answer("{\"a\":1} x");

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(Role.USER, "Hi")), Map.of("type", "object")))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(output).contains("op=chat", "outcome=failed error=AiUnavailableException");
        assertThat(output).doesNotContain("outcome=ok");
    }

    @Test
    @DisplayName("AC-001.3 an answer without a message is a failure")
    void missingMessageFails() {
        ollama.stubFor(post("/api/chat")
                .willReturn(aResponse().withStatus(200).withBody("{}").withHeader("Content-Type", "application/json")));

        assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(Role.USER, "Hi")), null))
                .isInstanceOf(AiUnavailableException.class);
    }
}
