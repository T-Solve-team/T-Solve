package vn.tsolve.common.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithTSolveUserSecurityContextFactory implements WithSecurityContextFactory<WithTSolveUser> {
    @Override
    public SecurityContext createSecurityContext(WithTSolveUser annotation) {
        var user = new TSolveUser(
                UUID.fromString(annotation.id()),
                annotation.email(),
                annotation.fullName(),
                new HashSet<>(Arrays.asList(annotation.roles())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        return context;
    }
}
