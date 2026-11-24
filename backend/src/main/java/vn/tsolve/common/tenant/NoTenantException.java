package vn.tsolve.common.tenant;

/**
 * No Workspace is bound to the thread (AC-002.16). A plain {@link RuntimeException}, so the global handler answers it
 * with 500 and {@code MSG06}. The message holds no Workspace id.
 */
public class NoTenantException extends RuntimeException {

    public NoTenantException(String message) {
        super(message);
    }
}
