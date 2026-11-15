package vn.tsolve.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectConnectionTestRequest(
        @NotBlank(message = "MSG01") @Size(max = 20, message = "MSG02")
        String jiraProjectKey) {}
