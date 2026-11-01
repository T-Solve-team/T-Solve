package vn.tsolve.common.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** The claim checks of a FIT after its signature verified (AC-001.4, AC-001.5). */
public final class FitValidators {

    public static final String ISSUER = "forge/invocation-token";

    /** Accepts a server clock up to this far from Atlassian's (edge case 6). */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private FitValidators() {}

    public static OAuth2TokenValidator<Jwt> forApp(String appId) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtClaimValidator<String>("iss", ISSUER::equals),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(appId)),
                FitValidators::hasCloudId));
    }

    /** The FIT's {@code context.cloudId}; call only on a FIT that passed {@link #forApp}. */
    public static String cloudId(Jwt fit) {
        Map<String, Object> context = fit.getClaimAsMap("context");
        return context != null && context.get("cloudId") instanceof String id ? id : null;
    }

    private static OAuth2TokenValidatorResult hasCloudId(Jwt fit) {
        String cloudId = cloudId(fit);
        return cloudId != null && !cloudId.isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "FIT has no cloudId", null));
    }
}
