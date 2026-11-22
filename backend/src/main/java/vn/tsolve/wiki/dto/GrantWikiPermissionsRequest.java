package vn.tsolve.wiki.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record GrantWikiPermissionsRequest(
        @NotNull List<@NotNull UUID> departmentIds, @NotNull List<@NotNull UUID> userIds) {}
