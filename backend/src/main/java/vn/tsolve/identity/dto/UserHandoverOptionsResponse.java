package vn.tsolve.identity.dto;

import java.util.List;

public record UserHandoverOptionsResponse(List<ProjectOption> projects, List<SpaceOption> spaces) {
    public record ProjectOption(UserProjectResponse project, List<UserAccountResponse> recipients) {}

    public record SpaceOption(SpaceRefResponse space, List<UserAccountResponse> recipients) {}
}
