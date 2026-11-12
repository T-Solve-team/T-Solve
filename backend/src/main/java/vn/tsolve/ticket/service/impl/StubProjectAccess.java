package vn.tsolve.ticket.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Stand-in until the workspace module owns Projects; delete it when the real {@link ProjectAccess} lands.
 *
 * <p>Fails closed: a user manages only the Projects listed for them in {@code tsolve.stub.projects} ({@code
 * alice:<uuid>,<uuid>;bob:<uuid>}), so an empty setting lets nobody import.
 */
@Component
public class StubProjectAccess implements ProjectAccess {

    private final Map<String, Set<UUID>> managed = new HashMap<>();
    private final Set<UUID> inactive = new HashSet<>();
    private final Map<UUID, String> jiraKeys = new HashMap<>();

    public StubProjectAccess(String projects, String inactiveProjects) {
        this(projects, inactiveProjects, "");
    }

    /** {@code jiraKeys} = {@code <project uuid>:<Jira key>,…}, read until project-integration links real Projects. */
    @Autowired
    public StubProjectAccess(
            @Value("${tsolve.stub.projects:}") String projects,
            @Value("${tsolve.stub.inactive-projects:}") String inactiveProjects,
            @Value("${tsolve.stub.jira-keys:}") String jiraKeys) {
        for (String entry : projects.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                managed.put(parts[0].trim(), ids(parts[1]));
            }
        }
        inactive.addAll(ids(inactiveProjects));
        for (String entry : jiraKeys.split(",")) {
            String[] parts = entry.split(":", 2);
            Set<UUID> id = parts.length == 2 ? ids(parts[0]) : Set.of();
            if (id.size() == 1 && !parts[1].isBlank()) {
                this.jiraKeys.put(id.iterator().next(), parts[1].trim());
            }
        }
    }

    @Override
    public boolean managesActive(String user, UUID projectId) {
        return managed.getOrDefault(user, Set.of()).contains(projectId) && isActive(projectId);
    }

    @Override
    public boolean isActive(UUID projectId) {
        return !inactive.contains(projectId);
    }

    @Override
    public Optional<String> jiraProjectKey(UUID projectId) {
        return Optional.ofNullable(jiraKeys.get(projectId));
    }

    private static Set<UUID> ids(String list) {
        Set<UUID> ids = new HashSet<>();
        for (String id : list.split(",")) {
            String trimmed = id.trim();
            try {
                UUID uuid = UUID.fromString(trimmed);
                // UUID.fromString also takes short forms such as 1-1-1-1-1; only the canonical form counts, in any case
                if (uuid.toString().equalsIgnoreCase(trimmed)) {
                    ids.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
                // a malformed id grants nothing
            }
        }
        return ids;
    }
}
