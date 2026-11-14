package vn.tsolve.common.session.lookup;

import java.util.List;
import java.util.UUID;
import vn.tsolve.common.session.dto.SpaceRef;

/**
 * Implemented by wiki. Returns the Spaces of the current tenant (from {@code TenantContext}) that the Account or its
 * Department can View, plus the ones it manages; any order, never null. Until a bean exists, callers answer empty.
 */
public interface ViewableSpaceLookup {
    List<SpaceRef> findViewableSpaces(UUID accountId);
}
