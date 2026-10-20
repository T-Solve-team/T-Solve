package vn.tsolve.ticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Authentication, session/CSRF transport and Project rights are supplied by the identity integration. */
@Configuration
@EnableMethodSecurity
public class ReviewSecurityConfig {}
