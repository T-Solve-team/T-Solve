package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tsolve.ticket.dto.SolutionSections;

public record SolutionRewriteJobResult(
        Status status,
        @JsonInclude(JsonInclude.Include.NON_NULL) SolutionSections sections) {
    public enum Status {
        PENDING,
        DONE,
        FAILED
    }
}
