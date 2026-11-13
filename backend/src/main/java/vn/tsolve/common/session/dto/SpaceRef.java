package vn.tsolve.common.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SpaceRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID spaceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
