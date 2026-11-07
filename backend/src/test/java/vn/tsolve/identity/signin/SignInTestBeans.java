package vn.tsolve.identity.signin;

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

    @Bean
    @Primary
    RecordingTenantScope recordingTenantScope() {
        return new RecordingTenantScope();
    }
}
