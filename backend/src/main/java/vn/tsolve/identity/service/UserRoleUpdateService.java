package vn.tsolve.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.UserDetailResponse;

public interface UserRoleUpdateService {
    UserDetailResponse update(UUID userId, JsonNode body, TSolveUser caller);
}
