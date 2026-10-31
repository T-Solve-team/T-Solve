package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

/** Shared values and doubles for the sign-in tests. */
public final class SignInTestFixtures {

    /** Not the production origins; the port proves the URLs come from config. */
    public static final String LOGIN_ORIGIN = "http://login.localhost:8081";

    public static final String ADMIN_ORIGIN = "http://admin.localhost:8081";

    /** Base64 of 32 zero bytes (an obviously fake, low-entropy key). */
    public static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    /** Base64 of 32 bytes of {@code 0x01} (a second fake key, different from {@link #KEY}). */
    public static final String OTHER_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

    /** MSG141, Report3 text. */
    public static final String MSG141_TEXT =
            "This sign-in request is not valid or has expired. Please start the sign-in again.";

    public static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    private SignInTestFixtures() {}

    public static SignInProperties properties() {
        return properties(KEY);
    }

    public static SignInProperties properties(String signingKey) {
        return new SignInProperties(
                URI.create(LOGIN_ORIGIN),
                URI.create(ADMIN_ORIGIN),
                signingKey,
                "test-client-id",
                "test-secret",
                "ops@example.test");
    }

    /** Ways a browser can send a state value that must not be trusted. {@code valid} is {@code <id>.<mac>}. */
    public enum BadState {
        PAYLOAD_EDITED(true) {
            @Override
            public String make(String valid, SignedToken token) {
                char first = valid.charAt(0);
                return (first == '0' ? "1" : "0") + valid.substring(1);
            }
        },
        MAC_EDITED(true) {
            @Override
            public String make(String valid, SignedToken token) {
                // the first MAC character carries six full bits, so changing it always changes the MAC
                int macStart = valid.indexOf('.') + 1;
                char first = valid.charAt(macStart);
                return valid.substring(0, macStart) + (first == 'A' ? 'B' : 'A') + valid.substring(macStart + 1);
            }
        },
        BARE_ID(true) {
            @Override
            public String make(String valid, SignedToken token) {
                return valid.substring(0, valid.indexOf('.'));
            }
        },
        SIGNED_WITH_OTHER_KEY(true) {
            @Override
            public String make(String valid, SignedToken token) {
                return new SignedToken(properties(OTHER_KEY)).sign(valid.substring(0, valid.indexOf('.')));
            }
        },
        WELL_SIGNED_UNKNOWN_ID(false) {
            @Override
            public String make(String valid, SignedToken token) {
                return token.sign(UUID.randomUUID().toString());
            }
        },
        WELL_SIGNED_NOT_A_UUID(false) {
            @Override
            public String make(String valid, SignedToken token) {
                return token.sign("not-a-uuid");
            }
        },
        NULL(true) {
            @Override
            public String make(String valid, SignedToken token) {
                return null;
            }
        },
        EMPTY(true) {
            @Override
            public String make(String valid, SignedToken token) {
                return "";
            }
        };

        private final boolean badSignature;

        BadState(boolean badSignature) {
            this.badSignature = badSignature;
        }

        /** True when the signature check alone must reject it, before any database read. */
        public boolean badSignature() {
            return badSignature;
        }

        public abstract String make(String valid, SignedToken token);
    }

    /**
     * An in-memory {@link SignInStateRepository}: a Mockito mock whose default answer keeps saved states in a map.
     * It dispatches on the method name, so it compiles against the skeleton (only {@code markUsed}) and serves the
     * final {@code JpaRepository} ({@code save}, {@code findById}) without a change. {@code markUsed} follows the
     * planned SQL: one row only when unused and {@code expires_at > usedAt}. A test may still stub it.
     */
    public static final class StateStore implements Answer<Object> {

        private final Map<UUID, SignInState> rows = new LinkedHashMap<>();
        private final List<SignInState> saved = new ArrayList<>();
        private final SignInStateRepository repository = Mockito.mock(SignInStateRepository.class, this);

        public SignInStateRepository repository() {
            return repository;
        }

        public synchronized List<SignInState> saved() {
            return List.copyOf(saved);
        }

        public SignInState onlySaved() {
            List<SignInState> all = saved();
            assertThat(all).as("saved sign-in states").hasSize(1);
            return all.get(0);
        }

        /** How many times the repository method of this name was called since the last reset. */
        public long calls(String methodName) {
            return Mockito.mockingDetails(repository).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                    .count();
        }

        public void clearCalls() {
            Mockito.clearInvocations(repository);
        }

        public synchronized void reset() {
            rows.clear();
            saved.clear();
            Mockito.reset(repository);
        }

        @Override
        public synchronized Object answer(InvocationOnMock invocation) throws Throwable {
            switch (invocation.getMethod().getName()) {
                case "save", "saveAndFlush" -> {
                    SignInState state = invocation.getArgument(0);
                    if (state.getId() == null) {
                        ReflectionTestUtils.setField(state, "id", UUID.randomUUID());
                    }
                    saved.add(state);
                    rows.put(state.getId(), state);
                    return state;
                }
                case "findById" -> {
                    return Optional.ofNullable(rows.get(invocation.getArgument(0)));
                }
                case "markUsed" -> {
                    SignInState state = rows.get(invocation.getArgument(0));
                    Instant usedAt = invocation.getArgument(1);
                    if (state == null
                            || usedAt == null
                            || state.getUsedAt() != null
                            || !state.getExpiresAt().isAfter(usedAt)) {
                        return 0;
                    }
                    ReflectionTestUtils.setField(state, "usedAt", usedAt);
                    return 1;
                }
                default -> {
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
                }
            }
        }
    }
}
