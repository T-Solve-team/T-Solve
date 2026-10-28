package vn.tsolve.ticket.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Stand-in until the workspace module owns Projects; delete it when the real {@link ProjectAccess} lands.
 *
 * <p>Fails closed: a user manages only the Projects listed for them in {@code tsolve.stub.projects} ({@code
 * alice:1,2;bob:3}), so an empty setting lets nobody import.
 */
@Component
public class StubProjectAccess implements ProjectAccess {

    private final Map<String, Set<Long>> managed = new HashMap<>();
    private final Set<Long> inactive = new HashSet<>();

    public StubProjectAccess(
            @Value("${tsolve.stub.projects:}") String projects,
            @Value("${tsolve.stub.inactive-projects:}") String inactiveProjects) {
        for (String entry : projects.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                managed.put(parts[0].trim(), ids(parts[1]));
            }
        }
        inactive.addAll(ids(inactiveProjects));
    }

    @Override
    public boolean managesActive(String user, long projectId) {
        return managed.getOrDefault(user, Set.of()).contains(projectId) && isActive(projectId);
    }

    @Override
    public boolean isActive(long projectId) {
        return !inactive.contains(projectId);
    }

    private static Set<Long> ids(String list) {
        Set<Long> ids = new HashSet<>();
        for (String id : list.split(",")) {
            try {
                ids.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException ignored) {
                // a malformed id grants nothing
            }
        }
        return ids;
    }
}
