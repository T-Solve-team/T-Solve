package vn.tsolve.identity.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.FieldMessageCodeException;

/** Keeps field types intact so the service can validate them in the mock's order. */
public record CreateUserRequest(JsonNode body) {
    public static CreateUserRequest parse(String json, ObjectMapper mapper) {
        try {
            JsonNode body = json == null
                    ? null
                    : mapper.reader()
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(json);
            if (body == null || !body.isObject()) {
                throw invalidBody();
            }
            return new CreateUserRequest(body);
        } catch (JsonProcessingException malformed) {
            throw invalidBody();
        }
    }

    private static FieldMessageCodeException invalidBody() {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG03", Map.of(), null);
    }
}
