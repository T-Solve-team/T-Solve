package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class IdTokenVerifierTest {

    private static final String NONCE = "nonce-of-this-state";

    private IdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        FakeGoogleIssuer.reset();
        verifier = verifierAt(MutableClock.START);
    }

    private static IdTokenVerifier verifierAt(Instant now) {
        return new IdTokenVerifier(
                SignInTestFixtures.properties(),
                FakeGoogleIssuer.jwksUri(),
                FakeGoogleIssuer.ISSUER,
                new IdentityClock(Clock.fixed(now, ZoneOffset.UTC)));
    }

    private Optional<GoogleIdentity> verify(String token) {
        AtomicReference<Optional<GoogleIdentity>> result = new AtomicReference<>();
        assertThatCode(() -> result.set(verifier.verify(token, NONCE))).doesNotThrowAnyException();
        return result.get();
    }

    private static Consumer<Map<String, Object>> nothing() {
        return claims -> {};
    }

    @Test
    @DisplayName("AC-001.9 accepts a valid RS256 ID token and returns email, hd and name")
    void acceptsAValidToken_AC_001_9() {
        Optional<GoogleIdentity> identity = verify(FakeGoogleIssuer.idToken(NONCE, nothing()));

        assertThat(identity).isPresent();
        assertThat(identity.get().subject()).isEqualTo("google-sub-1");
        assertThat(identity.get().email()).isEqualTo("lan.tran@acme.example");
        assertThat(identity.get().hostedDomain()).isEqualTo("acme.example");
        assertThat(identity.get().name()).isEqualTo("Lan Tran");
    }

    @Test
    @DisplayName("AC-001.9 accepts the issuer without a scheme (accounts.google.com)")
    void acceptsTheIssuerWithoutAScheme_AC_001_9() {
        assertThat(verify(FakeGoogleIssuer.idToken(NONCE, claims -> claims.put("iss", "accounts.google.com"))))
                .isPresent();
    }

    @Test
    @DisplayName("AC-001.9 / edge 22 rejects a token signed with another key under a known kid")
    void rejectsAnotherKey_AC_001_9_edge22() {
        assertThat(verify(FakeGoogleIssuer.signedWithOtherKey(NONCE, nothing())))
                .isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / edge 22 rejects a token whose kid is not in the JWKS")
    void rejectsUnknownKid_AC_001_9_edge22() {
        assertThat(verify(FakeGoogleIssuer.withUnknownKid(NONCE, nothing()))).isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / edge 22 rejects alg none")
    void rejectsAlgNone_AC_001_9_edge22() {
        assertThat(verify(FakeGoogleIssuer.unsigned(NONCE, nothing()))).isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / edge 22 rejects HS256 signed with the client secret")
    void rejectsHs256_AC_001_9_edge22() {
        assertThat(verify(FakeGoogleIssuer.hs256(FakeGoogleIssuer.CLIENT_SECRET, NONCE, nothing())))
                .isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"https://evil.example", "https://accounts.google.com.evil.example"})
    @NullSource
    @DisplayName("AC-001.9 / edge 22 rejects a wrong issuer")
    void rejectsAWrongIssuer_AC_001_9_edge22(String issuer) {
        String token = FakeGoogleIssuer.idToken(NONCE, claims -> {
            if (issuer == null) {
                claims.remove("iss");
            } else {
                claims.put("iss", issuer);
            }
        });

        assertThat(verify(token)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"another-client-id"})
    @NullSource
    @DisplayName("AC-001.9 / edge 22 rejects a wrong audience")
    void rejectsAWrongAudience_AC_001_9_edge22(String audience) {
        String token = FakeGoogleIssuer.idToken(NONCE, claims -> {
            if (audience == null) {
                claims.remove("aud");
            } else {
                claims.put("aud", audience);
            }
        });

        assertThat(verify(token)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / edge 22 rejects an expired token")
    void rejectsAnExpiredToken_AC_001_9_edge22() {
        long fiveMinutesAgo = MutableClock.START.minusSeconds(300).getEpochSecond();

        assertThat(verify(FakeGoogleIssuer.idToken(NONCE, claims -> claims.put("exp", fiveMinutesAgo))))
                .isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / Q9 measures expiry with IdentityClock")
    void measuresExpiryWithIdentityClock_AC_001_9_Q9() {
        // exp is in the real past but in the future of the fixed clock: accepted
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");
        String pastButFuture = FakeGoogleIssuer.idToken(fixed, NONCE, nothing());
        assertThat(verifierAt(fixed).verify(pastButFuture, NONCE)).isPresent();

        // exp is in the real future but in the past of the fixed clock: rejected
        Instant farFuture = Instant.parse("2040-01-01T00:00:00Z");
        String futureButPast = FakeGoogleIssuer.idToken(Instant.parse("2035-01-01T00:00:00Z"), NONCE, nothing());
        assertThat(verifierAt(farFuture).verify(futureButPast, NONCE)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"nonce-of-another-state"})
    @NullSource
    @DisplayName("AC-001.9 / edge 22 rejects a nonce that is not the state's")
    void rejectsAWrongNonce_AC_001_9_edge22(String nonce) {
        assertThat(verify(FakeGoogleIssuer.idToken(nonce, nothing()))).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"false", "missing", "string-false"})
    @DisplayName("AC-001.9 / edge 22 rejects email_verified that is not true")
    void rejectsUnverifiedEmail_AC_001_9_edge22(String kind) {
        String token = FakeGoogleIssuer.idToken(NONCE, claims -> {
            switch (kind) {
                case "false" -> claims.put("email_verified", Boolean.FALSE);
                case "missing" -> claims.remove("email_verified");
                default -> claims.put("email_verified", "false");
            }
        });

        assertThat(verify(token)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.9 / edge 52 rejects a token with no email claim")
    void rejectsATokenWithNoEmail_AC_001_9_edge52() {
        assertThat(verify(FakeGoogleIssuer.idToken(NONCE, claims -> claims.remove("email"))))
                .isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "abc", "aaa.bbb"})
    @DisplayName("AC-001.9 rejects a string that is not a JWT")
    void rejectsAStringThatIsNotAJwt_AC_001_9(String text) {
        assertThat(verify(text)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"status-500", "connection-reset"})
    @DisplayName("AC-001.9 / edge 23 rejects when the JWKS cannot be fetched")
    void rejectsWhenTheJwksCannotBeFetched_AC_001_9_edge23(String how) {
        if (how.equals("status-500")) {
            FakeGoogleIssuer.jwksDown();
        } else {
            FakeGoogleIssuer.jwksReset();
        }

        assertThat(verify(FakeGoogleIssuer.idToken(NONCE, nothing()))).isEmpty();
    }

    @Test
    @DisplayName("edge 33 logs no ID token")
    void logsNoIdToken_edge33(CapturedOutput output) {
        String token = FakeGoogleIssuer.signedWithOtherKey(NONCE, nothing());

        assertThat(verify(token)).isEmpty();

        assertThat(output.getAll()).doesNotContain(token);
    }
}
