package vn.tsolve.identity.signin;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds {@link SignInProperties}; the application does not start while one of its values is missing. */
@Configuration
@EnableConfigurationProperties(SignInProperties.class)
public class SignInConfig {

    @Bean
    public IdentityClock identityClock() {
        return new IdentityClock(Clock.systemUTC());
    }
}
