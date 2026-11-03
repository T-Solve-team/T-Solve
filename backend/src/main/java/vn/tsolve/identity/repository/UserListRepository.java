package vn.tsolve.identity.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.identity.dto.UserListDepartmentRef;
import vn.tsolve.identity.dto.UserListItem;
import vn.tsolve.identity.dto.UserListQuery;

@Repository
@ConditionalOnProperty(name = "tsolve.sql-workspace.enabled", havingValue = "true")
public class UserListRepository {
    public record AccountAccess(String status, Set<Role> roles) {}

    private final JdbcTemplate jdbc;

    public UserListRepository(@Qualifier("sqlTenantDatabase") TenantDatabase database) {
        jdbc = database.jdbc();
    }

    public Page<UserListItem> list(UserListQuery query) {
        var predicates = new ArrayList<String>();
        var arguments = new ArrayList<Object>();
        if (query.q() != null) {
            predicates.add("(u.full_name ILIKE ? ESCAPE '\\' OR u.company_email ILIKE ? ESCAPE '\\')");
            String pattern =
                    "%" + query.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        if (query.role() == Role.EMPLOYEE) {
            predicates.add("NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id=u.user_id)");
        } else if (query.role() != null) {
            predicates.add("EXISTS (SELECT 1 FROM user_role r WHERE r.user_id=u.user_id AND r.role=?)");
            arguments.add(query.role().name());
        }
        if (query.status() != null) {
            predicates.add("u.account_status=?");
            arguments.add(query.status());
        }
        if (query.departmentId() != null) {
            predicates.add("u.department_id=?");
            arguments.add(query.departmentId());
        }
        String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        Long total = jdbc.queryForObject("SELECT count(*) FROM app_user u" + where, Long.class, arguments.toArray());
        String requestedOrder = query.pageable().getSort().stream()
                .map(order -> {
                    String column =
                            switch (order.getProperty()) {
                                case "fullName" -> "u.full_name";
                                case "email" -> "u.company_email";
                                default -> throw new IllegalArgumentException("Unsupported user sort");
                            };
                    return column + " " + order.getDirection().name();
                })
                .collect(Collectors.joining(", "));
        String order = requestedOrder.isEmpty() ? "u.full_name ASC" : requestedOrder;
        arguments.add(query.pageable().getPageSize());
        arguments.add(query.pageable().getOffset());
        var rows = jdbc.query(
                "SELECT u.user_id,u.full_name,u.company_email,u.account_status,"
                        + " d.department_id,d.name AS department_name FROM app_user u"
                        + " LEFT JOIN department d ON d.department_id=u.department_id" + where
                        + " ORDER BY " + order + ", u.user_id ASC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Long departmentId = rs.getObject("department_id", Long.class);
                    var department = departmentId == null
                            ? null
                            : new UserListDepartmentRef(id(departmentId), rs.getString("department_name"));
                    String status = rs.getString("account_status");
                    if (!List.of("ACTIVE", "DEACTIVATED").contains(status)) throw new TenantAccessException();
                    return new UserListItem(
                            id(rs.getLong("user_id")),
                            rs.getString("full_name"),
                            rs.getString("company_email"),
                            List.of(Role.EMPLOYEE),
                            department,
                            status);
                },
                arguments.toArray());
        var roles = new HashMap<String, EnumSet<Role>>();
        rows.forEach(user -> roles.put(user.id(), EnumSet.of(Role.EMPLOYEE)));
        if (!rows.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
            jdbc.query(
                    "SELECT user_id,role FROM user_role WHERE user_id IN (" + placeholders + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            rs -> roles.get(id(rs.getLong("user_id"))).add(Role.valueOf(rs.getString("role"))),
                    rows.stream().map(user -> Long.valueOf(user.id())).toArray());
        }
        var content = rows.stream()
                .map(user -> new UserListItem(
                        user.id(),
                        user.fullName(),
                        user.email(),
                        List.copyOf(roles.get(user.id())),
                        user.department(),
                        user.status()))
                .toList();
        return new PageImpl<>(content, query.pageable(), java.util.Objects.requireNonNull(total));
    }

    public List<UserListDepartmentRef> departments() {
        return jdbc.query(
                "SELECT department_id,name FROM department ORDER BY name,department_id",
                (rs, row) -> new UserListDepartmentRef(id(rs.getLong(1)), rs.getString(2)));
    }

    public Optional<AccountAccess> accountAccess(long accountId) {
        return jdbc.query(
                "SELECT u.account_status,r.role FROM app_user u"
                        + " LEFT JOIN user_role r ON r.user_id=u.user_id WHERE u.user_id=?",
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    String status = rs.getString("account_status");
                    var roles = EnumSet.of(Role.EMPLOYEE);
                    do {
                        String role = rs.getString("role");
                        if (role != null) roles.add(Role.valueOf(role));
                    } while (rs.next());
                    return Optional.of(new AccountAccess(status, Set.copyOf(roles)));
                },
                accountId);
    }

    private static String id(long id) {
        if (id <= 0) throw new TenantAccessException();
        return Long.toString(id);
    }
}
