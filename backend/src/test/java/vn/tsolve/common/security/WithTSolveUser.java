package vn.tsolve.common.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithTSolveUserSecurityContextFactory.class)
public @interface WithTSolveUser {
    String id() default "00000000-0000-0000-0000-000000000001";

    String email() default "user@example.test";

    String fullName() default "Test User";

    Role[] roles() default {};
}
