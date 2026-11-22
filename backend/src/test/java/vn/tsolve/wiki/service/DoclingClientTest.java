package vn.tsolve.wiki.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

class DoclingClientTest {
    private static final String PATH = "/v1/convert/file";
    private final WireMockServer server = new WireMockServer(options().dynamicPort());

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void postsTheStoredBytesAndRequestsMarkdown_AC_007_4() {
        byte[] bytes = {0, 1, 2, 3};
        server.stubFor(post(PATH)
                .withMultipartRequestBody(aMultipart("files")
                        .withHeader("Content-Type", equalTo("application/pdf"))
                        .withBody(binaryEqualTo(bytes)))
                .withMultipartRequestBody(aMultipart("to_formats").withBody(equalTo("md")))
                .willReturn(okJson("{\"document\":{\"md_content\":\"# Heading\\nText\"}}")));

        assertThat(new DoclingClient(server.baseUrl(), "2s")
                        .convert(() -> new ByteArrayInputStream(bytes), "application/pdf"))
                .isEqualTo("# Heading\nText");
    }

    @ParameterizedTest
    @ValueSource(strings = {"error", "timeout", "blank", "failure"})
    void rejectsUnusableConversions_AC_007_8(String problem) {
        var response =
                switch (problem) {
                    case "error" -> aResponse().withStatus(503);
                    case "timeout" -> okJson("{}").withFixedDelay(1500);
                    case "failure" -> okJson("{\"status\":\"failure\",\"document\":{\"md_content\":\"Partial\"}}");
                    default -> okJson("{\"document\":{\"md_content\":\"\\n \\t\"}}");
                };
        server.stubFor(post(PATH).willReturn(response));
        var client = new DoclingClient(server.baseUrl(), problem.equals("timeout") ? "500ms" : "120s");
        var expected =
                switch (problem) {
                    case "error" -> RestClientResponseException.class;
                    case "timeout" -> ResourceAccessException.class;
                    default -> IllegalStateException.class;
                };
        assertThatThrownBy(() -> client.convert(() -> new ByteArrayInputStream(new byte[] {1}), "application/pdf"))
                .isInstanceOf(expected);
    }
}
