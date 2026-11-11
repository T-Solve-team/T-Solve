package vn.tsolve.identity.session;

import java.time.Instant;
import java.util.UUID;

/** One signed-in browser session as kept in Redis (design: Session row). */
public record WebSession(
        Kind kind,
        UUID workspaceId,
        UUID accountId,
        String operatorEmail,
        String siteHost,
        String idTokenHint,
        Instant createdAt,
        Instant lastActivityAt) {

    public enum Kind {
        WORKSPACE,
        OPERATOR
    }
}
