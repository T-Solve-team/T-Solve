package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tsolve.ticket.dto.SolutionSections;

public record SolutionRewriteJobResult(
        JiraAppJobStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) SolutionSections sections) {}
