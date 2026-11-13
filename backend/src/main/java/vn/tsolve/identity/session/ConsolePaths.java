package vn.tsolve.identity.session;

import java.util.Set;

/**
 * The requests an Operator session may make on {@code admin.}: every other request, whatever its path, is refused with
 * MSG08 by {@link WebSessionFilter} before the endpoint runs. Entries are {@code "METHOD /path"}, matched exactly by
 * method and path.
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
