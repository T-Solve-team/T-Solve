package vn.tsolve.identity.service;

import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;

public interface UserDetailService {
    UserDetailResponse getDetail(UUID userId, TSolveUser caller);

    UserHandoverOptionsResponse getHandoverOptions(UUID userId, TSolveUser caller);
}
