package vn.tsolve.identity.signin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Creates, checks and consumes sign-in state values. A state value is {@code <id>.<mac>}; the row behind it holds
 * the target, the filtered {@code returnTo}, the nonce and the PKCE verifier, which never go to the browser.
 */
@Service
public class SignInStateService {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final String DEFAULT_RETURN_TO = "/";
    static final int MAX_RETURN_TO_LENGTH = 2048;

    private static final int RANDOM_BYTES = 32;

    private final SignInStateRepository repository;
    private final SignedToken signedToken;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SignInStateService(SignInStateRepository repository, SignedToken signedToken, Clock clock) {
        this.repository = repository;
        this.signedToken = signedToken;
        this.clock = clock;
    }

    /** A state for the Workspace the platform resolved from the host. */
    public String createForWorkspace(UUID workspaceId, String returnTo) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("A Workspace sign-in state needs a Workspace");
        }
        return create(SignInState.Target.WORKSPACE, workspaceId, returnTo);
    }

    public String createForPlatformConsole(String returnTo) {
        return create(SignInState.Target.PLATFORM_CONSOLE, null, returnTo);
    }

    /** The state when the value is signed by this server, known, not expired and not used. Does not consume it. */
    public Optional<SignInState> check(String stateValue) {
        return signedToken
                .verify(stateValue)
                .flatMap(SignInStateService::parseId)
                .flatMap(repository::findById)
                .filter(state -> state.getUsedAt() == null)
                .filter(state -> clock.instant().isBefore(state.getExpiresAt()));
    }

    /** As {@link #check}, then marks the state used; of two requests with the same value only one gets it. */
    public Optional<SignInState> consume(String stateValue) {
        return check(stateValue).filter(state -> repository.markUsed(state.getId(), clock.instant()) == 1);
    }

    private String create(SignInState.Target target, UUID workspaceId, String returnTo) {
        Instant now = clock.instant();
        SignInState state = new SignInState(
                target,
                workspaceId,
                null,
                filterReturnTo(returnTo),
                randomValue(),
                randomValue(),
                now,
                now.plus(LIFETIME));
        SignInState saved = repository.save(state);
        return signedToken.sign(saved.getId().toString());
    }

    /** 43 URL-safe characters, which is also a valid PKCE verifier (RFC 7636). */
    private String randomValue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Optional<UUID> parseId(String payload) {
        try {
            return Optional.of(UUID.fromString(payload));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Keeps {@code returnTo} only when it is a path of the same site: one leading slash, no backslash, no ASCII
     * control or space character, at most 2,048 characters (AC-001.3). Anything else becomes {@code /}.
     */
    static String filterReturnTo(String returnTo) {
        if (returnTo == null
                || returnTo.isEmpty()
                || returnTo.length() > MAX_RETURN_TO_LENGTH
                || returnTo.charAt(0) != '/'
                || (returnTo.length() > 1 && returnTo.charAt(1) == '/')) {
            return DEFAULT_RETURN_TO;
        }
        for (int i = 0; i < returnTo.length(); i++) {
            char c = returnTo.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '\\') {
                return DEFAULT_RETURN_TO;
            }
        }
        return returnTo;
    }
}
