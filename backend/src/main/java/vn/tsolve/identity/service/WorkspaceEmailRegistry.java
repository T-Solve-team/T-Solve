package vn.tsolve.identity.service;

import java.util.UUID;

public interface WorkspaceEmailRegistry {
    boolean claim(UUID workspaceId, String email);

    void release(UUID workspaceId, String email);
}
