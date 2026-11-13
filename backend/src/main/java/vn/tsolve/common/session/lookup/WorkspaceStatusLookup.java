package vn.tsolve.common.session.lookup;

/**
 * Implemented by workspace, which reads the Workspace bound to the request. True means company information is
 * entered, not that the platform's status is ACTIVE.
 */
public interface WorkspaceStatusLookup {
    boolean isCompanyInformationEntered();
}
