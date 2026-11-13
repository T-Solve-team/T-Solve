package vn.tsolve.identity.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Test-only: puts the {@link OperatorProbeTestController} endpoint on the Console list (never in main code). */
@Configuration(proxyBeanMethods = false)
class ConsolePathsTestConfig {

    @Bean
    @Primary
    ConsolePaths testConsolePaths() {
        return ConsolePaths.of("POST /api/v1/auth/logout", "GET /api/v1/test/operator");
    }
}
