package vn.tsolve.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import vn.tsolve.ticket.csv.CsvRowValidator;

/** One Solution for one or several rows of a preview of a file without Solutions (STORY-013). */
public record FillSolutionRequest(
        @NotEmpty List<@NotNull @Min(1) Integer> rowNumbers,
        String symptoms,
        @NotBlank String rootCause,
        @NotBlank String resolutionSteps,
        String workaround,
        String scope) {

    // Only Root cause and Resolution steps are required (KAN-474); a blank Symptoms, Workaround or Scope becomes
    // null here. Sections are stripped here, before validation: @NotBlank checks with String.trim, which keeps Unicode
    // spaces
    // such as U+3000, so a section of only those would pass and be saved empty (BUG-151).
    public FillSolutionRequest {
        symptoms = CsvRowValidator.trim(symptoms);
        rootCause = CsvRowValidator.trim(rootCause);
        resolutionSteps = CsvRowValidator.trim(resolutionSteps);
        workaround = CsvRowValidator.trim(workaround);
        scope = CsvRowValidator.trim(scope);
    }
}
