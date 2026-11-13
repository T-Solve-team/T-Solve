package vn.tsolve.common.session.lookup;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.common.session.dto.DepartmentRef;

/** Implemented by identity. Returns the Department of a Project Manager, only an ACTIVE one, otherwise empty. */
public interface DepartmentLookup {
    Optional<DepartmentRef> findDepartment(UUID userId);
}
