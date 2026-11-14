package vn.tsolve.identity.service;

import java.util.List;
import org.springframework.data.domain.Page;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.identity.dto.MySolutionsQuery;

public interface MyProfileService {
    List<SpaceRef> getMyViewableSpaces();

    Page<MySolution> getMySolutions(MySolutionsQuery query);
}
