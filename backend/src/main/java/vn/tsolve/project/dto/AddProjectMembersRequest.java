package vn.tsolve.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AddProjectMembersRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> userIds) {}
