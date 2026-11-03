package vn.tsolve.identity.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import vn.tsolve.identity.dto.UserListDepartmentRef;
import vn.tsolve.identity.dto.UserListItem;
import vn.tsolve.identity.dto.UserListQuery;

public interface UserListService {
    Page<UserListItem> list(UserListQuery query, Authentication authentication);

    List<UserListDepartmentRef> departments(Authentication authentication);
}
