package vn.tsolve.common.session.lookup;

import java.util.List;
import java.util.UUID;
import vn.tsolve.common.session.dto.SpaceRef;

/** Implemented by wiki. Returns the Spaces the user manages as Knowledge Manager, only with the Role KNOWLEDGE_MANAGER. */
public interface ManagedSpaceLookup {
    List<SpaceRef> findManagedSpaces(UUID userId);
}
