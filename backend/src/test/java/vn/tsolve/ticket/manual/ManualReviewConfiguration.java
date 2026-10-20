package vn.tsolve.ticket.manual;

import static org.springframework.security.config.Customizer.withDefaults;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import vn.tsolve.ticket.dto.ReviewActor;
import vn.tsolve.ticket.service.ReviewAccessService;

/** Explicitly imported by the test-only main class; never scanned into the production application. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("manual-review")
@Import(ManualReviewToolsController.class)
public class ManualReviewConfiguration {
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manualLoopbackOnly() {
        return server -> server.setAddress(InetAddress.getLoopbackAddress());
    }

    @Bean
    UserDetailsService manualUsers(@Value("${MANUAL_REVIEW_PASSWORD}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("MANUAL_REVIEW_PASSWORD is required");
        return new InMemoryUserDetailsManager(
                User.withUsername("pm")
                        .password("{noop}" + password)
                        .roles("PROJECT_MANAGER", "MANUAL_REVIEW_OPERATOR")
                        .build(),
                User.withUsername("otherpm")
                        .password("{noop}" + password)
                        .roles("PROJECT_MANAGER")
                        .build(),
                User.withUsername("employee")
                        .password("{noop}" + password)
                        .roles("EMPLOYEE")
                        .build());
    }

    @Bean
    SecurityFilterChain manualSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests -> requests.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .httpBasic(withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .build();
    }

    @Bean
    ReviewAccessService manualReviewAccess() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) return null;
            return switch (authentication.getName()) {
                case "pm" -> new ReviewActor("pm", "Manual Project Manager", Map.of(10L, "Manual IT"), Set.of(10L));
                case "otherpm" -> new ReviewActor(
                        "otherpm", "Other Project Manager", Map.of(20L, "Manual HR"), Set.of(20L));
                case "employee" -> new ReviewActor("employee", "Manual Employee", Map.of(), Set.of(10L));
                default -> null;
            };
        };
    }

    @Bean
    OpenAPI manualApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "manualBasic",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("manualBasic"));
    }

    @Bean
    ApplicationRunner seedManualReview(JdbcTemplate jdbc, ManualReviewToolsController tools) {
        return args -> {
            String database = jdbc.queryForObject("select current_database()", String.class);
            if (!"review_manual".equals(database)) {
                throw new IllegalStateException("Manual fixtures require the isolated review_manual database");
            }
            tools.seedFixtures();
        };
    }
}
