package vn.tsolve.workspace.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperties;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.FieldMessageCodeException;

/** Keeps JSON field types for ordered service validation; never prints the request's token. */
@Schema(
        name = "WorkspaceSetupRequest",
        type = "object",
        requiredProperties = {"companyName", "jiraSiteUrl", "serviceAccountEmail", "serviceAccountToken"})
@SchemaProperties({
    @SchemaProperty(name = "companyName", schema = @Schema(type = "string", minLength = 1, maxLength = 100)),
    @SchemaProperty(name = "jiraSiteUrl", schema = @Schema(type = "string", minLength = 1, maxLength = 200)),
    @SchemaProperty(
            name = "serviceAccountEmail",
            schema = @Schema(type = "string", format = "email", minLength = 1, maxLength = 100)),
    @SchemaProperty(
            name = "serviceAccountToken",
            schema =
                    @Schema(
                            type = "string",
                            format = "password",
                            minLength = 1,
                            accessMode = Schema.AccessMode.WRITE_ONLY))
})
public record WorkspaceSetupRequest(@Schema(hidden = true) JsonNode body) {
    public static WorkspaceSetupRequest parse(String json, ObjectMapper mapper) {
        try {
            JsonNode body = json == null
                    ? null
                    : mapper.reader()
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(json);
            if (body == null || !body.isObject()) {
                throw invalidBody();
            }
            return new WorkspaceSetupRequest(body);
        } catch (JsonProcessingException malformed) {
            throw invalidBody();
        }
    }

    private static FieldMessageCodeException invalidBody() {
        return new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG03", Map.of(), null);
    }

    @Override
    public String toString() {
        return "WorkspaceSetupRequest[redacted]";
    }
}
