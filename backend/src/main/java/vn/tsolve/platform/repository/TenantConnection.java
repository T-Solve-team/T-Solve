package vn.tsolve.platform.repository;

/** Internal catalog projection; never returned by a service or endpoint. */
public record TenantConnection(
        long workspaceId, String host, int port, String database, String username, String passwordReference) {
    @Override
    public String toString() {
        return "TenantConnection[workspaceId=" + workspaceId + "]";
    }
}
