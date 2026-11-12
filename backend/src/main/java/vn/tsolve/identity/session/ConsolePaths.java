package vn.tsolve.identity.session;

import java.util.Set;

/**
 * The {@code /api/**} endpoints an Operator session may reach on {@code admin.}: every other API request is refused
 * with MSG08 by {@link WebSessionFilter} before the endpoint runs. Entries are {@code "METHOD /path"}.
 */
public record ConsolePaths(Set<String> entries) {

    public ConsolePaths {
        entries = Set.copyOf(entries);
    }

    public static ConsolePaths of(String... entries) {
        return new ConsolePaths(Set.of(entries));
    }

    public boolean allows(String method, String path) {
        return entries.contains(method + " " + path);
    }
}
