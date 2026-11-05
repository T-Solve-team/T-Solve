package vn.tsolve.identity.signin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.RecordingTenantScope;

/**
 * Beans the sign-in slice tests add: a movable clock (replacing the app's Clock), the {@link IdentityClock} over it,
 * the in-memory Workspaces, the in-memory state repository, and the bound {@link SignInProperties}.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(SignInProperties.class)
public class SignInTestBeans {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    IdentityClock identityClock(MutableClock clock) {
        return new IdentityClock(clock);
    }

    @Bean
    InMemoryWorkspaceLookup inMemoryWorkspaceLookup() {
        return new InMemoryWorkspaceLookup();
    }

    @Bean
    StateStore signInStateStore() {
        return new StateStore();
    }

    @Bean
    SignInStateRepository signInStateRepository(StateStore store) {
        return store.repository();
    }

    @Bean
    @Primary
    RecordingTenantScope recordingTenantScope() {
        return new RecordingTenantScope();
    }

    @Bean
    AccountStore accountStore(RecordingTenantScope scope) {
        return new AccountStore(scope);
    }

    @Bean
    AccountRepository accountRepository(AccountStore store) {
        return store.repository();
    }

    @Bean
    HandOffStore handOffStore() {
        return new HandOffStore();
    }

    @Bean
    HandOffCodeRepository handOffCodeRepository(HandOffStore store) {
        return store.repository();
    }
}
