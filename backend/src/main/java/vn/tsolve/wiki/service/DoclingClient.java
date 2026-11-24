package vn.tsolve.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DoclingClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public DoclingClient(
            @Value("${DOCLING_BASE_URL:}") String baseUrl,
            @Value("${tsolve.wiki.docling.timeout:120s}") String timeout) {
        if (baseUrl.isBlank()) {
            client = null;
        } else {
            Duration duration = DurationStyle.detectAndParse(timeout);
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    // A down docling host fails fast, so the index job falls back without waiting the long read
                    // timeout.
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build());
            factory.setReadTimeout(duration);
            client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build();
        }
    }

    public String convert(Supplier<InputStream> original, String contentType) {
        if (client == null) throw new IllegalStateException("Docling is unconfigured");
        var mediaType = MediaType.parseMediaType(contentType);
        String fileName = MediaType.APPLICATION_PDF.equals(mediaType) ? "document.pdf" : "document.docx";
        try (var stream = original.get()) {
            var multipart = new MultipartBodyBuilder();
            multipart.part("files", new InputStreamResource(stream), mediaType).filename(fileName);
            multipart.part("to_formats", "md");
            multipart.part("do_ocr", "false");
            JsonNode response = client.post()
                    .uri("/v1/convert/file")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || "failure".equals(response.path("status").asText())) {
                throw new IllegalStateException("Docling conversion failed");
            }
            JsonNode markdown = response.path("document").path("md_content");
            if (!markdown.isTextual() || markdown.textValue().isBlank()) {
                throw new IllegalStateException("Docling returned no Markdown");
            }
            return markdown.textValue();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
