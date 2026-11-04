package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.identity.signin.SignInTestFixtures.BadState;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

class SignInStateServiceTest {

    private static final UUID ACME_ID = InMemoryWorkspaceLookup.ACME.id();
    private static final Duration JUST_BEFORE_EXPIRY = Duration.ofMinutes(9).plusSeconds(59);
    private static final Duration JUST_AFTER_EXPIRY = Duration.ofMinutes(10).plusSeconds(1);

    private final StateStore store = new StateStore();
    private final SignInStateRepository repository = store.repository();
    private final MutableClock clock = new MutableClock();
    private final SignedToken signedToken = new SignedToken(SignInTestFixtures.properties());
    private final SignInStateService service =
            new SignInStateService(repository, signedToken, new IdentityClock(clock));

    @Test
    @DisplayName("AC-001.2 creates a Workspace state with exactly one target, kept returnTo and a 10-minute life")
    void createsWorkspaceState_AC_001_2() {
        service.createForWorkspace(ACME_ID, "/tickets/42");

        SignInState saved = store.onlySaved();
        assertThat(saved.getTarget()).isEqualTo(SignInState.Target.WORKSPACE);
        assertThat(saved.getWorkspaceId()).isEqualTo(ACME_ID);
        assertThat(saved.getTrackerAccountId()).isNull();
        assertThat(saved.getReturnTo()).isEqualTo("/tickets/42");
        assertThat(saved.getCreatedAt()).isEqualTo(MutableClock.START);
        assertThat(saved.getExpiresAt()).isEqualTo(MutableClock.START.plus(SignInTestFixtures.STATE_LIFETIME));
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("AC-001.28 creates a Platform Console state with no Workspace")
    void createsPlatformConsoleState_AC_001_28() {
        service.createForPlatformConsole("/workspaces");

        SignInState saved = store.onlySaved();
        assertThat(saved.getTarget()).isEqualTo(SignInState.Target.PLATFORM_CONSOLE);
        assertThat(saved.getWorkspaceId()).isNull();
        assertThat(saved.getTrackerAccountId()).isNull();
        assertThat(saved.getReturnTo()).isEqualTo("/workspaces");
        assertThat(saved.getCreatedAt()).isEqualTo(MutableClock.START);
        assertThat(saved.getExpiresAt()).isEqualTo(MutableClock.START.plus(SignInTestFixtures.STATE_LIFETIME));
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("AC-001.2 refuses a Workspace state without a Workspace")
    void refusesWorkspaceStateWithoutWorkspace_AC_001_2() {
        assertThatThrownBy(() -> service.createForWorkspace(null, "/"))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
        assertThat(store.calls("save")).isZero();
        assertThat(store.saved()).isEmpty();
    }

    @Test
    @DisplayName("AC-001.2 gives the browser only the signed id, never the nonce or verifier")
    void givesTheBrowserOnlyTheSignedId_AC_001_2() {
        String value = service.createForWorkspace(ACME_ID, "/");

        SignInState saved = store.onlySaved();
        String id = saved.getId().toString();
        assertThat(value).matches("^" + Pattern.quote(id) + "\\.[A-Za-z0-9_-]+$");
        assertThat(signedToken.verify(value)).contains(id);
        assertThat(value).doesNotContain(saved.getNonce()).doesNotContain(saved.getPkceVerifier());
    }

    @Test
    @DisplayName("AC-001.2 keeps a random nonce and PKCE verifier on the server")
    void keepsRandomNonceAndVerifierOnTheServer_AC_001_2() {
        service.createForWorkspace(ACME_ID, "/");
        service.createForWorkspace(ACME_ID, "/");

        List<SignInState> saved = store.saved();
        assertThat(saved).hasSize(2);
        for (SignInState state : saved) {
            // RFC 7636: 43-128 unreserved characters
            assertThat(state.getPkceVerifier()).matches("^[A-Za-z0-9\\-._~]{43,128}$");
            assertThat(state.getNonce()).matches("^[A-Za-z0-9_-]{22,}$");
        }
        assertThat(saved.get(0).getId()).isNotEqualTo(saved.get(1).getId());
        assertThat(saved.get(0).getNonce()).isNotEqualTo(saved.get(1).getNonce());
        assertThat(saved.get(0).getPkceVerifier()).isNotEqualTo(saved.get(1).getPkceVerifier());
    }

    @Test
    @DisplayName("AC-001.2 accepts a fresh state until just before 10 minutes")
    void acceptsFreshStateUntilJustBeforeTenMinutes_AC_001_2() {
        String value = service.createForWorkspace(ACME_ID, "/");
        UUID id = store.onlySaved().getId();

        assertThat(service.check(value)).map(SignInState::getId).contains(id);
        clock.advance(JUST_BEFORE_EXPIRY);
        assertThat(service.check(value)).map(SignInState::getId).contains(id);
        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("AC-001.2 / edge 20 rejects an expired state")
    void rejectsExpiredState_AC_001_2_edge20() {
        String value = service.createForWorkspace(ACME_ID, "/");

        clock.advance(JUST_AFTER_EXPIRY);

        assertThat(service.check(value)).isEmpty();
        assertThat(service.consume(value)).isEmpty();
        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("AC-001.2 / edge 20 rejects a state that was already used")
    void rejectsUsedState_AC_001_2_edge20() {
        String value = service.createForWorkspace(ACME_ID, "/");
        ReflectionTestUtils.setField(store.onlySaved(), "usedAt", MutableClock.START);

        assertThat(service.check(value)).isEmpty();
        assertThat(service.consume(value)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(BadState.class)
    @DisplayName("edge 20 rejects edited, unsigned, foreign-key or unknown values before trusting them")
    void rejectsUntrustedValues_edge20(BadState bad) {
        String valid = service.createForWorkspace(ACME_ID, "/");
        String value = bad.make(valid, signedToken);
        store.clearCalls();
        AtomicReference<Optional<SignInState>> result = new AtomicReference<>();

        assertThatCode(() -> result.set(service.check(value))).doesNotThrowAnyException();
        assertThat(result.get()).isEmpty();
        if (bad.badSignature()) {
            assertThat(store.calls("findById"))
                    .as("database read before the signature check")
                    .isZero();
        }
    }

    @Test
    @DisplayName("AC-001.2 lets a state be consumed once")
    void letsStateBeConsumedOnce_AC_001_2() {
        String value = service.createForWorkspace(ACME_ID, "/");
        UUID id = store.onlySaved().getId();
        // a second callback racing the first: the row count alone must decide
        doReturn(1, 0).when(repository).markUsed(any(), any());

        assertThat(service.consume(value)).map(SignInState::getId).contains(id);
        verify(repository).markUsed(id, MutableClock.START);
        assertThat(service.consume(value)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.2 / Q9 takes createdAt and expiresAt from IdentityClock")
    void takesTimesFromIdentityClock_AC_001_2_Q9() {
        Instant time = Instant.parse("2031-05-06T07:08:09Z");
        SignInStateService other =
                new SignInStateService(repository, signedToken, new IdentityClock(Clock.fixed(time, ZoneOffset.UTC)));

        other.createForWorkspace(ACME_ID, "/");

        SignInState saved = store.onlySaved();
        assertThat(saved.getCreatedAt()).isEqualTo(time);
        assertThat(saved.getExpiresAt()).isEqualTo(time.plus(SignInTestFixtures.STATE_LIFETIME));
    }

    @Test
    @DisplayName("AC-001.44 authorizeOnce marks the checked state with the hash and the IdentityClock time")
    void authorizeOnceMarksTheState_AC_001_44() {
        String value = service.createForWorkspace(ACME_ID, "/");
        UUID id = store.onlySaved().getId();
        byte[] hash = new byte[32];
        hash[0] = 7;

        Optional<SignInState> result = service.authorizeOnce(value, hash);

        assertThat(result).map(SignInState::getId).contains(id);
        verify(repository).markAuthorized(id, hash, MutableClock.START);
    }

    @Test
    @DisplayName("AC-001.45 authorizeOnce returns empty when the state is already authorized")
    void authorizeOnceReturnsEmptyWhenAlreadyAuthorized_AC_001_45() {
        String value = service.createForWorkspace(ACME_ID, "/");
        doReturn(0).when(repository).markAuthorized(any(), any(), any());

        assertThat(service.authorizeOnce(value, new byte[32])).isEmpty();
    }

    enum Untouchable {
        EDITED,
        EXPIRED,
        USED
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Untouchable.class)
    @DisplayName("AC-001.45 authorizeOnce does not touch an invalid, expired or used state")
    void authorizeOnceDoesNotTouchAnInvalidState_AC_001_45(Untouchable kind) {
        String valid = service.createForWorkspace(ACME_ID, "/");
        String value = valid;
        switch (kind) {
            case EDITED -> value = BadState.MAC_EDITED.make(valid, signedToken);
            case EXPIRED -> clock.advance(JUST_AFTER_EXPIRY);
            case USED -> ReflectionTestUtils.setField(store.onlySaved(), "usedAt", MutableClock.START);
        }

        assertThat(service.authorizeOnce(value, new byte[32])).isEmpty();
        verify(repository, never()).markAuthorized(any(), any(), any());
    }

    @Test
    @DisplayName("AC-001.46 consume still consumes a state that was never authorized")
    void consumeStillConsumesAStateThatWasNeverAuthorized_AC_001_46() {
        String value = service.createForWorkspace(ACME_ID, "/");
        UUID id = store.onlySaved().getId();

        Optional<SignInState> consumed = service.consume(value);

        assertThat(consumed).map(SignInState::getId).contains(id);
        assertThat(consumed.get().getAuthorizedAt()).isNull();
        verify(repository, times(1)).markUsed(id, MutableClock.START);
    }

    static Stream<String> keptReturnTo() {
        return Stream.of("/", "/tickets/42", "/search?q=a%20b#top", "/" + "a".repeat(2047));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("keptReturnTo")
    @DisplayName("AC-001.3 keeps a same-site relative returnTo")
    void keepsSameSiteRelativeReturnTo_AC_001_3(String returnTo) {
        service.createForWorkspace(ACME_ID, returnTo);

        assertThat(store.onlySaved().getReturnTo()).isEqualTo(returnTo);
    }

    static Stream<Arguments> replacedReturnTo() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("empty", ""),
                Arguments.of("no leading slash", "tickets"),
                Arguments.of("protocol-relative", "//evil.example"),
                Arguments.of("triple slash", "///evil.example"),
                Arguments.of("slash backslash", "/\\evil"),
                Arguments.of("double backslash", "\\\\evil"),
                Arguments.of("backslash inside", "/a\\b"),
                Arguments.of("absolute URL", "https://evil.example"),
                Arguments.of("javascript scheme", "javascript:alert(1)"),
                Arguments.of("tab", "/a\tb"),
                Arguments.of("leading space", " /a"),
                Arguments.of("space", "/a b"),
                Arguments.of("line feed", "/a\nb"),
                Arguments.of("carriage return", "/a\rb"),
                Arguments.of("NUL", "/a\u0000b"),
                Arguments.of("DEL", "/a\u007Fb"),
                Arguments.of("2,049 characters", "/" + "a".repeat(2048)),
                Arguments.of("3,000 characters", "/" + "a".repeat(2999)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("replacedReturnTo")
    @DisplayName("AC-001.3 / edge 19 replaces any other returnTo with /")
    void replacesAnyOtherReturnToWithRoot_AC_001_3_edge19(String label, String returnTo) {
        service.createForWorkspace(ACME_ID, returnTo);
        service.createForPlatformConsole(returnTo);

        assertThat(store.saved()).extracting(SignInState::getReturnTo).containsExactly("/", "/");
    }
}
