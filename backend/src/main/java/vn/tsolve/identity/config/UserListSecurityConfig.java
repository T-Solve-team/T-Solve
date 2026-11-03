package vn.tsolve.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.session.UserListSessionFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tsolve.sql-workspace.enabled", havingValue = "true")
public class UserListSecurityConfig {
    @Bean
    @Order(2)
    SecurityFilterChain userListReadChain(
            HttpSecurity http,
            @Qualifier("sqlTenantDatabase") TenantDatabase database,
            UserListAccessService access,
            ObjectMapper mapper)
            throws Exception {
        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/api/v1/users", "GET"),
                        new AntPathRequestMatcher("/api/v1/users/departments", "GET")))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) ->
                                problem(mapper, response, HttpStatus.UNAUTHORIZED, "MSG07"))
                        .accessDeniedHandler((request, response, failure) ->
                                problem(mapper, response, HttpStatus.FORBIDDEN, "MSG08")))
                .addFilterAfter(
                        new UserListSessionFilter(database, access, mapper), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private static void problem(
            ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetails.of(status, code, Map.of()));
    }
}
