package vn.tsolve.identity.session;

import java.util.UUID;

/**
 * Published once when a web session ends. {@code sessionId} is the SHA-256 hex of the cookie value (the part of the
 * Redis key after the prefix), never the cookie value itself. It is published only after the key is gone.
 */
public record SessionEnded(UUID accountId, UUID workspaceId, String sessionId, Reason reason) {

    public enum Reason {
        LOGOUT,
        EXPIRED,
        DEACTIVATED
    }
}
