package vn.tsolve.project.dto;

import java.util.UUID;

public record MemberCandidateResponse(UUID userId, String fullName, String email) {}
