package vn.tsolve.tracker.dto;

import java.util.List;

/**
 * The actors of a project role: the people added to it one by one, and the names of the groups added to it, whose
 * members are not read (project-integration AC-011.3).
 */
public record TrackerRoleActors(List<Person> people, List<String> groups) {

    public TrackerRoleActors {
        people = List.copyOf(people);
        groups = List.copyOf(groups);
    }

    /** A user actor: the tracker account id and its display name. */
    public record Person(String accountId, String displayName) {}
}
