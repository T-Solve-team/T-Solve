package vn.tsolve.identity.mock;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.tsolve.identity.signin.port.TenantScope;

/** Interim mock — replace when KAN-275 workspace-session (identity Tasks 26a/26b) lands; never enable in prod. */
@Configuration(proxyBeanMethods = false)
@Profile("identity-mock")
@EnableConfigurationProperties(MockIdentityProperties.class)
public class MockIdentityConfiguration {
    @Bean
    FilterRegistrationBean<MockSignedInCallerFilter> mockSignedInCallerFilter(
            TenantScope scope, MockCallerReader reader, MockIdentityProperties properties) {
        var registration = new FilterRegistrationBean<>(new MockSignedInCallerFilter(scope, reader, properties));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
