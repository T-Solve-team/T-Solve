package vn.tsolve.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.ai.client.OllamaHttp.Operation;
import vn.tsolve.ai.exception.AiUnavailableException;

@ExtendWith(OutputCaptureExtension.class)
class OllamaHttpTest {

    private static final Map<String, Object> BODY = Map.of("model", "m", "prompt", "SECRET-PROMPT-TEXT");

    private final WireMockServer ollama = new WireMockServer(options().dynamicPort());

    @BeforeEach
    void start() {
        ollama.start();
    }

    @AfterEach
    void stop() {
        ollama.stop();
    }

    private OllamaHttp http(Duration chatTimeout, Duration embedTimeout, int maxConcurrent, Duration permitWait) {
        return new OllamaHttp(
                ollama.baseUrl(), Duration.ofSeconds(2), chatTimeout, embedTimeout, maxConcurrent, permitWait);
    }

    private OllamaHttp http() {
        return http(Duration.ofSeconds(5), Duration.ofSeconds(5), 2, Duration.ofSeconds(1));
    }

    private int chatRequests() {
        return ollama.countRequestsMatching(
                        postRequestedFor(urlEqualTo("/api/chat")).build())
                .getCount();
    }

    @Test
    @DisplayName("AC-003.1 posts to OLLAMA_BASE_URL: chat to /api/chat, embed to /api/embed")
    void postsToTheConfiguredServer() {
        ollama.stubFor(post("/api/chat").willReturn(okJson("{\"a\":1}")));
        ollama.stubFor(post("/api/embed").willReturn(okJson("{\"b\":2}")));

        assertThat(http().post(Operation.CHAT, "m", BODY).path("a").asInt()).isEqualTo(1);
        assertThat(http().post(Operation.EMBED, "m", BODY).path("b").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-003.2 / AC-003.4 / edge 4 a chat answer slower than AI_CHAT_TIMEOUT fails and is not sent again")
    void chatTimeoutFailsWithoutRetry() {
        ollama.stubFor(post("/api/chat").willReturn(okJson("{}").withFixedDelay(1500)));
        OllamaHttp http = http(Duration.ofMillis(300), Duration.ofSeconds(5), 2, Duration.ofSeconds(1));

        long started = System.nanoTime();
        assertThatThrownBy(() -> http.post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1400));
        assertThat(chatRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-003.2 an embedding slower than AI_EMBED_TIMEOUT fails")
    void embedTimeoutFails() {
        ollama.stubFor(post("/api/embed").willReturn(okJson("{}").withFixedDelay(1500)));
        OllamaHttp http = http(Duration.ofSeconds(5), Duration.ofMillis(300), 2, Duration.ofSeconds(1));

        assertThatThrownBy(() -> http.post(Operation.EMBED, "m", BODY)).isInstanceOf(AiUnavailableException.class);
        ollama.verify(1, postRequestedFor(urlEqualTo("/api/embed")));
    }

    @Test
    @DisplayName("AC-003.2 a connect timeout stops within AI_CONNECT_TIMEOUT")
    void connectTimeoutFails() {
        OllamaHttp http = new OllamaHttp(
                "http://192.0.2.1:81",
                Duration.ofMillis(250),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                2,
                Duration.ofSeconds(1));

        long started = System.nanoTime();
        assertThatThrownBy(() -> http.post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("AC-003.3 / edge 5 a 5xx is sent once more and the second answer is returned")
    void serverErrorIsRetriedOnce() {
        ollama.stubFor(post("/api/chat")
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("second"));
        ollama.stubFor(post("/api/chat")
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(okJson("{\"ok\":true}")));

        assertThat(http().post(Operation.CHAT, "m", BODY).path("ok").asBoolean())
                .isTrue();
        assertThat(chatRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-003.3 a dropped connection is sent once more")
    void connectionFailureIsRetried() {
        ollama.stubFor(post("/api/chat")
                .inScenario("reset")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("second"));
        ollama.stubFor(post("/api/chat")
                .inScenario("reset")
                .whenScenarioStateIs("second")
                .willReturn(okJson("{\"ok\":true}")));

        assertThat(http().post(Operation.CHAT, "m", BODY).path("ok").asBoolean())
                .isTrue();
        assertThat(chatRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-003.3 two 5xx in a row fail after exactly two requests")
    void twoServerErrorsFail() {
        ollama.stubFor(post("/api/chat").willReturn(serverError()));

        assertThatThrownBy(() -> http().post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);
        assertThat(chatRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-003.4 / edge 6 a 4xx (model not pulled) fails without a second request")
    void clientErrorIsNotRetried() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> http().post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);
        assertThat(chatRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-003.5 / edge 3 a third call while two are in flight fails after AI_PERMIT_WAIT without a request")
    void thirdCallWaitsThenFails() throws Exception {
        ollama.stubFor(post("/api/chat").willReturn(okJson("{}").withFixedDelay(1500)));
        OllamaHttp http = http(Duration.ofSeconds(5), Duration.ofSeconds(5), 2, Duration.ofMillis(200));
        CompletableFuture<?> first = CompletableFuture.runAsync(() -> http.post(Operation.CHAT, "m", BODY));
        CompletableFuture<?> second = CompletableFuture.runAsync(() -> http.post(Operation.CHAT, "m", BODY));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (chatRequests() < 2 && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(chatRequests()).isEqualTo(2);

        long started = System.nanoTime();
        assertThatThrownBy(() -> http.post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isBetween(Duration.ofMillis(150), Duration.ofMillis(1000));
        assertThat(chatRequests()).isEqualTo(2);
        CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("AC-003.5 / edge 1 a failed call gives its slot back")
    void failedCallReleasesItsSlot() {
        ollama.stubFor(post("/api/chat")
                .inScenario("slot")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(400))
                .willSetStateTo("ok"));
        ollama.stubFor(
                post("/api/chat").inScenario("slot").whenScenarioStateIs("ok").willReturn(okJson("{\"ok\":true}")));
        OllamaHttp http = http(Duration.ofSeconds(5), Duration.ofSeconds(5), 1, Duration.ofMillis(100));

        assertThatThrownBy(() -> http.post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);
        assertThat(http.post(Operation.CHAT, "m", BODY).path("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("AC-003.5 / edge 9 an interrupted wait for a slot fails and keeps the interrupt flag")
    void interruptKeepsFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> http().post(Operation.CHAT, "m", BODY)).isInstanceOf(AiUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(chatRequests()).isZero();
    }

    @Test
    @DisplayName("AC-003.6 a successful call logs the operation, model, duration and outcome but no content")
    void successLogHasNoContent(CapturedOutput output) {
        ollama.stubFor(post("/api/chat").willReturn(okJson("{\"message\":{\"content\":\"SECRET-ANSWER-TEXT\"}}")));

        http().post(Operation.CHAT, "m", BODY);

        assertThat(output).contains("op=chat", "model=m", "durationMs=", "outcome=ok");
        assertThat(output).doesNotContain("SECRET-PROMPT-TEXT", "SECRET-ANSWER-TEXT");
    }

    @Test
    @DisplayName("AC-003.6 / edge 2 a failure echoing the prompt logs and throws only the error class")
    void failureLogHasNoContent(CapturedOutput output) {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(400).withBody("bad: SECRET-PROMPT-TEXT")));

        assertThatThrownBy(() -> http().post(Operation.CHAT, "m", BODY))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("SECRET");
                    assertThat(e.getCause()).isNull();
                });

        assertThat(output).contains("op=chat", "outcome=failed", "error=HttpClientErrorException");
        assertThat(output).doesNotContain("SECRET-PROMPT-TEXT");
    }

    @Test
    @DisplayName("AC-003.5 AI_MAX_CONCURRENT must be positive at startup")
    void nonPositiveMaxConcurrentFails() {
        for (int value : new int[] {0, -1}) {
            assertThatThrownBy(() -> http(Duration.ofSeconds(5), Duration.ofSeconds(5), value, Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AI_MAX_CONCURRENT");
        }
    }
}
