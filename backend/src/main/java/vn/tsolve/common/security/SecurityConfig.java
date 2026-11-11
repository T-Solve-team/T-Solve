package vn.tsolve.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import vn.tsolve.common.config.MethodSecurityConfig;

@Configuration
@Import(MethodSecurityConfig.class)
public class SecurityConfig {
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain webSecurityChain(
            HttpSecurity http, ObjectMapper mapper, ObjectProvider<SessionAuthenticationFilter> sessionFilter)
            throws Exception {
        var problems = new SecurityProblemWriter(mapper);
        // always registered in the application (SessionConfig); absent only in a controller slice test
        sessionFilter.ifAvailable(filter -> http.addFilterBefore(filter, CsrfFilter.class));
        return http.authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "MSG07"))
                        .accessDeniedHandler((request, response, failure) -> {
                            boolean authenticated = TRUST_RESOLVER.isAuthenticated(
                                    SecurityContextHolder.getContext().getAuthentication());
                            problems.write(
                                    request,
                                    response,
                                    authenticated ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                                    !authenticated || failure instanceof CsrfException ? "MSG07" : "MSG08");
                        }))
                .build();
    }
}
