package vn.tsolve.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserDetailResponse(
        UUID id,
        String fullName,
        String email,
        List<Role> roles,
        DepartmentRefResponse department,
        AccountStatus status,
        Instant lastSignInAt,
        boolean isCurrentUser,
        List<UUID> ticketResolverProjectIds,
        List<UserProjectResponse> projects,
        List<SpaceRefResponse> managedSpaces) {
    public static UserDetailResponse from(
            UserAccountResponse account,
            boolean isCurrentUser,
            List<UUID> ticketResolverProjectIds,
            List<UserProjectResponse> projects,
            List<SpaceRefResponse> managedSpaces) {
        return new UserDetailResponse(
                account.id(),
                account.fullName(),
                account.email(),
                account.roles(),
                account.department(),
                account.status(),
                account.lastSignInAt(),
                isCurrentUser,
                ticketResolverProjectIds,
                projects,
                managedSpaces);
    }
}
