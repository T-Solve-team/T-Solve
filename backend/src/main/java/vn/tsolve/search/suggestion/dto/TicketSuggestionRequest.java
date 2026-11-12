package vn.tsolve.search.suggestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TicketSuggestionRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\x00]*") String summary,
        @Size(max = 32767) @Schema(nullable = true) String description) {}
