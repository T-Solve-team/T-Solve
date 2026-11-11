package vn.tsolve.identity.account;

import java.time.Instant;
import java.util.UUID;
import vn.tsolve.common.security.Role;

/** One row of {@code user_role}. A plain record: read through {@link AccountRoleRepository}, written by the seeder. */
public record AccountRole(UUID id, UUID userId, Role role, Instant assignedAt) {}
