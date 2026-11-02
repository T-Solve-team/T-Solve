package vn.tsolve.common.security;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public record TSolveUser(UUID id, String email, String fullName, Set<Role> roles) implements Serializable {
    public TSolveUser {
        Objects.requireNonNull(id);
        Objects.requireNonNull(email);
        Objects.requireNonNull(fullName);
        var effective = EnumSet.of(Role.EMPLOYEE);
        effective.addAll(roles);
        roles = Set.copyOf(effective);
    }

    public List<SimpleGrantedAuthority> authorities() {
        return roles.stream()
                .sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    public static Optional<TSolveUser> current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                        && authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof TSolveUser user
                ? Optional.of(user)
                : Optional.empty();
    }
}
