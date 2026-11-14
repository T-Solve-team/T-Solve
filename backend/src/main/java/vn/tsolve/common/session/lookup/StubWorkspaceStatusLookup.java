package vn.tsolve.common.session.lookup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Staging-only stub for BUG-205 / KAN-329 (AC-003.7): reports company information as entered so {@code /me} says the
 * Workspace is set up. Default off ({@value #PROPERTY}=false); remove it when the Workspace module's lookup (KAN-119)
 * lands.
 */
@Component
@ConditionalOnProperty(name = StubWorkspaceStatusLookup.PROPERTY, havingValue = "true")
public class StubWorkspaceStatusLookup implements WorkspaceStatusLookup {
    public static final String PROPERTY = "tsolve.stub.workspace-ready";

    @Override
    public boolean isCompanyInformationEntered() {
        return true;
    }
}
