package vn.tsolve.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @NotBlank(message = "MSG01") @Size(max = 50, message = "MSG02")
        String name) {
    public UpdateDepartmentRequest {
        // @NotBlank uses trim() (up to U+0020), but strip() uses Character.isWhitespace (D22).
        // Normalize Unicode-whitespace names so blank gets MSG01, never MSG02; keep non-blank length as received.
        if (name != null && name.isBlank()) {
            name = "";
        }
    }
}
