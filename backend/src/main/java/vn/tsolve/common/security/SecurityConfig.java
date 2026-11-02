package vn.tsolve.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.exception.ProblemDetails;

@Configuration
@Import(MethodSecurityConfig.class)
public class SecurityConfig {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain webSecurityChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
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
                                write(mapper, response, HttpStatus.UNAUTHORIZED, "MSG07"))
                        .accessDeniedHandler((request, response, failure) -> {
                            boolean authenticated = new AuthenticationTrustResolverImpl()
                                    .isAuthenticated(
                                            SecurityContextHolder.getContext().getAuthentication());
                            write(
                                    mapper,
                                    response,
                                    authenticated ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                                    !authenticated || failure instanceof CsrfException ? "MSG07" : "MSG08");
                        }))
                .build();
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetails.of(status, code, Map.of()));
    }
}
