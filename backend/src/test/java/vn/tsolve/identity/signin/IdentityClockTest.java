package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IdentityClockTest {

    @Test
    @DisplayName("Q9 IdentityClock gives the wrapped clock's time")
    void givesTheWrappedClocksTime_Q9() {
        Instant time = Instant.parse("2030-01-02T03:04:05Z");

        assertThat(new IdentityClock(Clock.fixed(time, ZoneOffset.UTC)).now()).isEqualTo(time);
    }

    @Test
    @DisplayName("Q9 SignInConfig defines IdentityClock and no java.time.Clock bean")
    void signInConfigDefinesIdentityClockAndNoClockBean_Q9() {
        new ApplicationContextRunner()
                .withUserConfiguration(SignInConfig.class)
                .withPropertyValues(
                        "tsolve.auth.login-origin=" + SignInTestFixtures.LOGIN_ORIGIN,
                        "tsolve.auth.admin-origin=" + SignInTestFixtures.ADMIN_ORIGIN,
                        "tsolve.auth.signing-key=" + SignInTestFixtures.KEY,
                        "tsolve.auth.google-client-id=test-client-id",
                        "tsolve.auth.google-client-secret=test-secret",
                        "tsolve.auth.operator-emails=ops@example.test",
                        "tsolve.auth.keycloak-web-client-secret=test-secret",
                        "tsolve.auth.keycloak-console-client-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(IdentityClock.class)).hasSize(1);
                    assertThat(context.getBeansOfType(Clock.class)).isEmpty();
                });
    }
}
