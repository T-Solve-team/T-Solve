package vn.tsolve.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserAccountResponse(
        UUID id,
        String fullName,
        String email,
        List<Role> roles,
        DepartmentRefResponse department,
        AccountStatus status,
        Instant lastSignInAt) {}
