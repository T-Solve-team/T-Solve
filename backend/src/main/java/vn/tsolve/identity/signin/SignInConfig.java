package vn.tsolve.identity.signin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link SignInProperties}; the application does not start while one of its values is missing. */
@Configuration
@EnableConfigurationProperties(SignInProperties.class)
public class SignInConfig {}
