package vn.tsolve.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import vn.tsolve.ai.exception.AiUnavailableException;

/**
 * The only class that talks to Ollama: bounded by time and by concurrent calls, one retry for a transient error, one
 * log line per call. Nothing it logs or throws carries a request or response body.
 */
@Component
class OllamaHttp {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaHttp.class);

    enum Operation {
        CHAT("/api/chat"),
        EMBED("/api/embed");

        private final String path;

        Operation(String path) {
            this.path = path;
        }
    }

    private final RestClient chatClient;
    private final RestClient embedClient;
    private final Semaphore slots;
    private final Duration permitWait;

    @Autowired
    OllamaHttp(
            @Value("${OLLAMA_BASE_URL}") String baseUrl,
            @Value("${AI_CONNECT_TIMEOUT:2s}") String connectTimeout,
            @Value("${AI_CHAT_TIMEOUT:60s}") String chatTimeout,
            @Value("${AI_EMBED_TIMEOUT:10s}") String embedTimeout,
            @Value("${AI_MAX_CONCURRENT:2}") int maxConcurrent,
            @Value("${AI_PERMIT_WAIT:1s}") String permitWait) {
        this(
                baseUrl,
                DurationStyle.detectAndParse(connectTimeout),
                DurationStyle.detectAndParse(chatTimeout),
                DurationStyle.detectAndParse(embedTimeout),
                maxConcurrent,
                DurationStyle.detectAndParse(permitWait));
    }

    OllamaHttp(
            String baseUrl,
            Duration connectTimeout,
            Duration chatTimeout,
            Duration embedTimeout,
            int maxConcurrent,
            Duration permitWait) {
        String url = required("OLLAMA_BASE_URL", baseUrl);
        if (maxConcurrent <= 0) {
            throw new IllegalStateException("AI_MAX_CONCURRENT must be positive");
        }
        this.chatClient = client(url, connectTimeout, chatTimeout);
        this.embedClient = client(url, connectTimeout, embedTimeout);
        this.slots = new Semaphore(maxConcurrent);
        this.permitWait = permitWait;
    }

    /** The value of a setting that has no default; an unset one (no value, or a placeholder left as is) stops startup. */
    static String required(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }

    private static RestClient client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** The JSON answer of Ollama; throws {@link AiUnavailableException} when there is no usable one. */
    JsonNode post(Operation operation, String model, Object body) {
        return post(operation, model, body, Function.identity());
    }

    <T> T post(Operation operation, String model, Object body, Function<JsonNode, T> parse) {
        String op = operation.name().toLowerCase(Locale.ROOT);
        long started = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(permitWait.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new BusyException();
            }
            JsonNode answer = sendWithOneRetry(operation, body);
            if (answer == null) {
                throw new EmptyAnswerException();
            }
            T result = parse.apply(answer);
            LOG.info("ai call op={} model={} durationMs={} outcome=ok", op, model, millisSince(started));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed(op, model, started, e);
        } catch (RuntimeException e) {
            throw failed(op, model, started, e);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private JsonNode sendWithOneRetry(Operation operation, Object body) {
        try {
            return send(operation, body);
        } catch (RuntimeException first) {
            if (!isTransient(first)) {
                throw first;
            }
            return send(operation, body);
        }
    }

    private JsonNode send(Operation operation, Object body) {
        RestClient client = operation == Operation.CHAT ? chatClient : embedClient;
        return client.post().uri(operation.path).body(body).retrieve().body(JsonNode.class);
    }

    /**
     * A 5xx or a failed connection is worth one more try; a timeout, a 4xx or an unreadable answer is not (AC-003.3,
     * AC-003.4).
     */
    private static boolean isTransient(RuntimeException e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        boolean connectionFailure = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof JsonProcessingException) {
                return false;
            }
            connectionFailure |= t instanceof IOException;
        }
        return connectionFailure;
    }

    private static AiUnavailableException failed(String op, String model, long started, Exception cause) {
        Class<?> type = cause.getClass();
        if (type.getEnclosingClass() != null && !(cause instanceof AiFailure)) {
            type = type.getEnclosingClass();
        }
        LOG.warn(
                "ai call op={} model={} durationMs={} outcome=failed error={}",
                op,
                model,
                millisSince(started),
                type.getSimpleName());
        return new AiUnavailableException();
    }

    private static long millisSince(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    /** Our own failures; carries no message, cause or stack so nothing can leak. */
    private abstract static class AiFailure extends RuntimeException {
        AiFailure() {
            super(null, null, false, false);
        }
    }

    private static final class BusyException extends AiFailure {
        BusyException() {}
    }

    private static final class EmptyAnswerException extends AiFailure {
        EmptyAnswerException() {}
    }
}
