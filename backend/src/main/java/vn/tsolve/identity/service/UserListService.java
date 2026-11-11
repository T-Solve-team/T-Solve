package vn.tsolve.identity.service;

import java.util.List;
import org.springframework.data.domain.Page;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserListQuery;

public interface UserListService {
    Page<UserAccountResponse> list(UserListQuery query);

    List<DepartmentRefResponse> departments();
}
