package vn.tsolve.wiki.dto;

import java.util.UUID;

public record WikiSpace(UUID spaceId, String name, String departmentName, String accessLevel) {}
