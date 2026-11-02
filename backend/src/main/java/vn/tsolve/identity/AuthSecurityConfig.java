package vn.tsolve.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import vn.tsolve.common.security.SecurityConfig;

/**
 * The redirect endpoints are public and stateless. The shared web chain protects other requests; the Jira App
 * retains its separate FIT chain.
 */
@Configuration
@Import(SecurityConfig.class)
public class AuthSecurityConfig {

    private static final RequestMatcher SIGN_IN_PATHS = new OrRequestMatcher(
            AntPathRequestMatcher.antMatcher("/api/v1/auth/sign-in"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/authorize"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/callback"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/complete"));

    @Bean
    @Order(1)
    SecurityFilterChain signInRedirectChain(HttpSecurity http) throws Exception {
        http.securityMatcher(SIGN_IN_PATHS)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
