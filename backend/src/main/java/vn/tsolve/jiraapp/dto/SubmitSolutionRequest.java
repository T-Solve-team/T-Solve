package vn.tsolve.jiraapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.tsolve.ticket.dto.SolutionSections;

/** Finish of the Solution Form: the issue summary (the ticket title) and the five sections. */
public record SubmitSolutionRequest(
        @NotNull @Size(max = 255) String summary,
        @NotNull @Valid SolutionSections sections) {}
