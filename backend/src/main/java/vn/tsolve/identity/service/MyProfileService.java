package vn.tsolve.identity.service;

import java.util.List;
import vn.tsolve.common.session.dto.SpaceRef;

public interface MyProfileService {
    List<SpaceRef> getMyViewableSpaces();
}
