package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class FitValidatorsTest {

    private final OAuth2TokenValidator<Jwt> validator = FitValidators.forApp(FitTokens.APP_ID);

    @Test
    void acceptsForgeFitForOurApp_AC_001_1() {
        assertThat(validator.validate(fit(b -> b)).hasErrors()).isFalse();
    }

    @Test
    void rejectsOtherIssuer_AC_001_4() {
        assertThat(validator.validate(fit(b -> b.issuer("someone-else"))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsFitForAnotherForgeApp_AC_001_4() {
        assertThat(validator
                        .validate(fit(b -> b.audience(List.of("ari:cloud:ecosystem::app/other"))))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void rejectsExpiredFit_AC_001_5() {
        Instant past = Instant.now().minusSeconds(600);
        assertThat(validator
                        .validate(fit(b -> b.issuedAt(past.minusSeconds(60)).expiresAt(past)))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void acceptsNotBeforeWithinClockSkew_AC_001_5() {
        assertThat(validator
                        .validate(fit(b -> b.notBefore(Instant.now().plusSeconds(30))))
                        .hasErrors())
                .isFalse();
    }

    @Test
    void rejectsNotBeforeBeyondClockSkew_AC_001_5() {
        assertThat(validator
                        .validate(fit(b -> b.notBefore(Instant.now().plusSeconds(120))))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void rejectsFitWithoutCloudId_AC_001_5() {
        assertThat(validator.validate(fit(b -> b.claim("context", Map.of()))).hasErrors())
                .isTrue();
    }

    private static Jwt fit(UnaryOperator<Jwt.Builder> change) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("fit")
                .header("alg", "RS256")
                .issuer(FitValidators.ISSUER)
                .audience(List.of(FitTokens.APP_ID))
                .issuedAt(now.minusSeconds(5))
                .notBefore(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(300))
                .claim("context", Map.of("cloudId", FitTokens.CLOUD_ID));
        return change.apply(builder).build();
    }
}
