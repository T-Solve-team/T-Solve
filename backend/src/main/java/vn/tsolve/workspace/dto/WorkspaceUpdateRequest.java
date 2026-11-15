package vn.tsolve.workspace.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperties;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

/** Keeps JSON field types for ordered service validation; never prints the request's token. */
@Schema(
        name = "WorkspaceUpdateRequest",
        type = "object",
        requiredProperties = {"companyName", "serviceAccountEmail"})
@SchemaProperties({
    @SchemaProperty(name = "companyName", schema = @Schema(type = "string", minLength = 1, maxLength = 100)),
    @SchemaProperty(
            name = "serviceAccountEmail",
            schema = @Schema(type = "string", format = "email", minLength = 1, maxLength = 100)),
    @SchemaProperty(
            name = "serviceAccountToken",
            schema =
                    @Schema(
                            type = "string",
                            format = "password",
                            accessMode = Schema.AccessMode.WRITE_ONLY,
                            description = "Absent or blank keeps the stored token."))
})
public record WorkspaceUpdateRequest(@Schema(hidden = true) JsonNode body) {
    public static WorkspaceUpdateRequest parse(String json, ObjectMapper mapper) {
        return new WorkspaceUpdateRequest(
                WorkspaceSetupRequest.parse(json, mapper).body());
    }

    @Override
    public String toString() {
        return "WorkspaceUpdateRequest[redacted]";
    }
}
