package vn.tsolve.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** One Solution for one or several rows of a preview of a file without Solutions (STORY-013). */
public record FillSolutionRequest(
        @NotEmpty List<@NotNull @Min(1) Integer> rowNumbers,
        @NotBlank String symptoms,
        @NotBlank String rootCause,
        @NotBlank String resolutionSteps,
        String workaround,
        @NotBlank String scope) {}
