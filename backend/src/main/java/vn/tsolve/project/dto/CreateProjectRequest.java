package vn.tsolve.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateProjectRequest(
        @NotBlank(message = "MSG01") @Size(max = 20, message = "MSG02")
        String jiraProjectKey,

        @NotEmpty(message = "MSG01") List<String> doneStatusIds,
        @NotNull(message = "MSG01") BigDecimal reviewRange) {}
