package vn.tsolve.identity;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Two chains. The first is for the four redirect endpoints of the sign-in, which a browser reaches without a
 * session: open, stateless, no CSRF (all four are GET). The second, lowest-order chain keeps every other path
 * behind a signed-in user: 401 with no login form and no HTTP Basic challenge. Declaring any chain turns off Spring
 * Boot's default one, so the second must exist or the rest of the API would be open. be-foundation's
 * {@code roles-security} replaces the second chain later.
 */
@Configuration
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

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain everythingElseChain(HttpSecurity http) throws Exception {
        // The servlet container re-dispatches a failed request to /error, outside the first chain's paths. Without
        // this, a 500 or 404 on a redirect path would turn into a 401 here. Only the container makes ERROR dispatches.
        http.authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
