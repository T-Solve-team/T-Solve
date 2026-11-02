package vn.tsolve.platform.web;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class WorkspaceHost {
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Set<String> RESERVED = Set.of("www", "staging", "admin", "login", "api");
    private final Pattern workspaceHost;

    public WorkspaceHost(String baseDomain) {
        if (baseDomain == null
                || baseDomain.length() > 253
                || !baseDomain.toLowerCase(Locale.ROOT).matches(LABEL + "(?:\\." + LABEL + ")*")) {
            throw new IllegalArgumentException("Invalid Workspace base domain");
        }
        workspaceHost = Pattern.compile(
                "^(" + LABEL + ")\\." + Pattern.quote(baseDomain.toLowerCase(Locale.ROOT)) + "(?::([0-9]{1,5}))?$");
    }

    public Optional<String> siteName(String host) {
        if (host == null) {
            return Optional.empty();
        }
        var match = workspaceHost.matcher(host.toLowerCase(Locale.ROOT));
        if (!match.matches() || RESERVED.contains(match.group(1))) {
            return Optional.empty();
        }
        if (match.group(2) != null) {
            int port = Integer.parseInt(match.group(2));
            if (port < 1 || port > 65535) {
                return Optional.empty();
            }
        }
        return Optional.of(match.group(1));
    }
}
