package vn.tsolve.chatbot.issue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotIssueRequest(
        @NotBlank(message = "MSG01") @Size(max = 255, message = "MSG02")
        String summary,

        @Size(max = 10000, message = "MSG02") String description,
        @NotNull(message = "MSG01") Long departmentId) {}
