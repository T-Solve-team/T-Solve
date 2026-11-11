package vn.tsolve.identity.signin;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.RecordingTenantScope;

/** Beans the sign-in slice tests add: the in-memory Workspaces and the bound {@link SignInProperties}. */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(SignInProperties.class)
public class SignInTestBeans {

    @Bean
    InMemoryWorkspaceLookup inMemoryWorkspaceLookup() {
        return new InMemoryWorkspaceLookup();
    }

    /** The callback handler's session pieces need the clock; the slice has no {@code SignInConfig}. */
    @Bean
    IdentityClock identityClock() {
        return new IdentityClock(Clock.systemUTC());
    }

    @Bean
    @Primary
    RecordingTenantScope recordingTenantScope() {
        return new RecordingTenantScope();
    }
}
