package vn.tsolve.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.service.impl.UserListTransaction;
import vn.tsolve.identity.session.UserListSessionFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tsolve.identity.user-list.enabled", havingValue = "true")
public class UserListSecurityConfig {
    @Bean
    @Order(2)
    SecurityFilterChain userListReadChain(
            HttpSecurity http, UserListTransaction database, UserListAccessService access, ObjectMapper mapper)
            throws Exception {
        var problems = new SecurityProblemWriter(mapper);
        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/api/v1/users", "GET"),
                        new AntPathRequestMatcher("/api/v1/users/departments", "GET")))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "MSG07"))
                        .accessDeniedHandler((request, response, failure) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN, "MSG08")))
                .addFilterAfter(
                        new UserListSessionFilter(database, access, problems), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
