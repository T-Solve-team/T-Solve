package vn.tsolve.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@Documented
@WithSecurityContext(factory = WithTSolveUserSecurityContextFactory.class)
public @interface WithTSolveUser {
    String id() default "00000000-0000-0000-0000-000000000001";

    String email() default "user@example.test";

    String fullName() default "Test User";

    Role[] roles() default {};
}
