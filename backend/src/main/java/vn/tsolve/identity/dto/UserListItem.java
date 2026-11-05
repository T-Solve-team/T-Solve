package vn.tsolve.identity.dto;

import java.util.List;
import java.util.UUID;
import vn.tsolve.common.security.Role;

public record UserListItem(
        UUID id, String fullName, String email, List<Role> roles, UserListDepartmentRef department, String status) {}
