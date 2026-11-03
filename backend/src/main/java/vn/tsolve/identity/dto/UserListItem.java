package vn.tsolve.identity.dto;

import java.util.List;
import vn.tsolve.common.security.Role;

public record UserListItem(
        String id, String fullName, String email, List<Role> roles, UserListDepartmentRef department, String status) {}
