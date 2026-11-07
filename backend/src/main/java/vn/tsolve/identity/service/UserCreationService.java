package vn.tsolve.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserCreationOptionsResponse;

public interface UserCreationService {
    UserAccountResponse create(JsonNode request);

    UserCreationOptionsResponse creationOptions();
}
