package vn.tsolve.identity.dto;

import java.util.List;

public record UserCreationOptionsResponse(String companyDomain, List<SpaceRefResponse> spaces) {}
