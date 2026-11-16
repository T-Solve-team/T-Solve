package vn.tsolve.wiki.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateWikiFolderRequest(
        @NotBlank(message = "MSG01") @Size(max = 100, message = "MSG02")
        String name,

        @JsonProperty(value = "parentId", required = true) UUID parentId) {
    public UpdateWikiFolderRequest {
        if (name != null && name.isBlank()) name = "";
    }
}
