package vn.tsolve.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateWikiFolderRequest(
        @NotBlank(message = "MSG01") @Size(max = 100, message = "MSG02")
        String name,

        UUID parentId) {
    public CreateWikiFolderRequest {
        // Keep nonblank length as received; NotBlank's trim does not recognize Unicode whitespace.
        if (name != null && name.isBlank()) name = "";
    }
}
