package vn.tsolve.identity.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.tsolve.identity.service.WorkspaceEmailRegistry;

/** Interim until platform's workspace_email registry (Task 14); MSG155 is enforced there. */
@Service
public class AcceptAllWorkspaceEmailRegistry implements WorkspaceEmailRegistry {
    @Override
    public boolean claim(UUID workspaceId, String email) {
        return true;
    }

    @Override
    public void release(UUID workspaceId, String email) {}
}
