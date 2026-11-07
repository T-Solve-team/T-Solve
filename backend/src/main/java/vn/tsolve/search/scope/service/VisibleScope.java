package vn.tsolve.search.scope.service;

import java.util.Optional;
import java.util.Set;
import vn.tsolve.common.security.TSolveUser;

/** Membership is supplied by identity/project integration, never by request filters. */
public interface VisibleScope {
    Scope of(TSolveUser user);

    record Scope(Set<Long> departmentIds, Set<Long> managedProjectIds, Optional<String> writer) {
        public Scope {
            departmentIds = Set.copyOf(departmentIds);
            managedProjectIds = Set.copyOf(managedProjectIds);
            writer = writer.filter(value -> !value.isBlank());
        }

        public boolean isEmpty() {
            return departmentIds.isEmpty() && managedProjectIds.isEmpty() && writer.isEmpty();
        }

        public static Scope empty() {
            return new Scope(Set.of(), Set.of(), Optional.empty());
        }
    }
}
