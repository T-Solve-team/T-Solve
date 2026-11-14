package vn.tsolve.identity.signin.port;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.WorkspaceRef.Status;

/**
 * The env stub (KAN-327): a stand-in until the platform adapter {@code PlatformWorkspaceLookup} exists; the adapter
 * replaces this class. It knows the ONE Workspace named in {@code tsolve.stub.workspaces}
 * ({@code TSOLVE_STUB_WORKSPACES}), ACTIVE. Format: {@code siteHost|slug|workspaceUuid|googleDomain|keycloakOrgId}.
 * More than one entry fails the startup, because every stub Workspace would share the one app database. Empty (the
 * default) knows no Workspace, so a sign-in start on a Workspace site answers 404. An invalid entry fails the startup.
 */
@Profile("!dev")
@Component
public class NoWorkspaceLookup implements WorkspaceLookup {

    private static final int FIELDS = 5;

    private final List<WorkspaceRef> workspaces;

    public NoWorkspaceLookup(@Value("${tsolve.stub.workspaces:}") String entries) {
        this.workspaces = parse(entries);
    }

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        String wanted = withoutPort(host);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return workspaces.stream().filter(w -> w.siteHost().equals(wanted)).findFirst();
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        return workspaces.stream().filter(w -> w.id().equals(id)).findFirst();
    }

    private static List<WorkspaceRef> parse(String raw) {
        List<WorkspaceRef> parsed = new ArrayList<>();
        if (raw == null) {
            return parsed;
        }
        String[] entries = raw.split(";", -1);
        long filled = Arrays.stream(entries).filter(e -> !e.isBlank()).count();
        if (filled > 1) {
            throw new IllegalArgumentException("tsolve.stub.workspaces: exactly one Workspace entry is allowed on this"
                    + " stack (every stub Workspace would share the one app database), found " + filled);
        }
        for (int i = 0; i < entries.length; i++) {
            if (!entries[i].isBlank()) {
                parsed.add(parseEntry(entries[i], i + 1));
            }
        }
        return List.copyOf(parsed);
    }

    private static WorkspaceRef parseEntry(String entry, int index) {
        String[] fields = entry.split("\\|", -1);
        String host = fields[0].trim().toLowerCase(Locale.ROOT);
        if (fields.length != FIELDS) {
            throw invalid(index, host, "needs " + FIELDS + " fields separated by '|'");
        }
        for (String field : fields) {
            if (field.isBlank()) {
                throw invalid(index, host, "has a blank field");
            }
        }
        UUID id;
        try {
            id = UUID.fromString(fields[2].trim());
        } catch (IllegalArgumentException e) {
            throw invalid(index, host, "has a Workspace id that is not a UUID");
        }
        return new WorkspaceRef(
                id, withoutPort(host), fields[3].trim(), Status.ACTIVE, fields[1].trim(), fields[4].trim());
    }

    private static String withoutPort(String host) {
        if (host == null) {
            return "";
        }
        String lower = host.trim().toLowerCase(Locale.ROOT);
        int colon = lower.lastIndexOf(':');
        return colon > 0 && lower.indexOf(':') == colon ? lower.substring(0, colon) : lower;
    }

    private static IllegalArgumentException invalid(int index, String host, String reason) {
        return new IllegalArgumentException(
                "tsolve.stub.workspaces: entry " + index + " (host '" + host + "') " + reason);
    }
}
