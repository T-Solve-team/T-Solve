package vn.tsolve.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on {@code @PreAuthorize} on every endpoint (backend/AGENTS.md §5). How users sign in (Google OIDC) is the
 * identity module's job; until then Spring Boot's default login applies.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {}
