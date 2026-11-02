package vn.tsolve.platform.config;

/** Bound only when explicitly enabled; disabled legacy installations need no catalog credentials. */
public record SqlWorkspaceProperties(String baseDomain, Catalog catalog, int poolMax, int maxPools) {
    public record Catalog(String host, int port, String database, String username, String password) {
        @Override
        public String toString() {
            return "Catalog[redacted]";
        }
    }
}
