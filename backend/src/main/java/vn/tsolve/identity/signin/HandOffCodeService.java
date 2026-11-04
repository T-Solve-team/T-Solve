package vn.tsolve.identity.signin;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Issues hand-off codes: stores the row and returns the signed {@code <id>.<mac>} value for the browser. */
@Service
public class HandOffCodeService {

    private static final Duration VALID_FOR = Duration.ofSeconds(60);

    private final HandOffCodeRepository codes;
    private final SignedToken signedToken;
    private final IdentityClock clock;

    public HandOffCodeService(HandOffCodeRepository codes, SignedToken signedToken, IdentityClock clock) {
        this.codes = codes;
        this.signedToken = signedToken;
        this.clock = clock;
    }

    public String issue(UUID workspaceId, UUID accountId, String siteHost, String returnTo) {
        Instant now = clock.now();
        HandOffCode saved = codes.save(
                HandOffCode.forWorkspace(workspaceId, accountId, siteHost, returnTo, now, now.plus(VALID_FOR)));
        return signedToken.sign(saved.getId().toString());
    }
}
