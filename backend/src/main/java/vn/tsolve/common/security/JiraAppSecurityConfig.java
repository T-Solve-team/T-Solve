package vn.tsolve.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * The security chain of the Jira App API: every call carries a FIT (STORY-001). Runs before the web app's chain
 * and matches only {@code /api/v1/jira-app/**}.
 */
@Configuration
public class JiraAppSecurityConfig {

    public static final String PATHS = "/api/v1/jira-app/**";

    private static final Logger log = LoggerFactory.getLogger(JiraAppSecurityConfig.class);

    @Bean
    @Order(1)
    public SecurityFilterChain jiraAppSecurityChain(
            HttpSecurity http, JwtDecoder fitJwtDecoder, JiraSiteLookup siteLookup, ObjectMapper objectMapper)
            throws Exception {
        SecurityProblemWriter problems = new SecurityProblemWriter(objectMapper);
        // Missing, malformed, badly signed, expired or foreign FIT (AC-001.2 to AC-001.5).
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                problems.write(response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG08);
        // A valid FIT from a site no Workspace registered (AC-001.6).
        AccessDeniedHandler forbidden =
                (request, response, e) -> problems.write(response, HttpStatus.FORBIDDEN, SecurityProblemWriter.MSG08);
        // The JWKS could not be fetched: our failure, not the caller's (AC-001.8).
        AuthenticationFailureHandler failure = (request, response, e) -> {
            if (e instanceof AuthenticationServiceException) {
                log.warn("FIT not verified: error type JWKS_UNAVAILABLE");
                problems.write(response, HttpStatus.SERVICE_UNAVAILABLE, SecurityProblemWriter.MSG06);
            } else {
                unauthorized.commence(request, response, e);
            }
        };

        return http.securityMatcher(PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole(Roles.JIRA_APP))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(fitJwtDecoder)
                                .jwtAuthenticationConverter(new FitAuthenticationConverter(siteLookup)))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(failure);
                                return filter;
                            }
                        }))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }
}
