package vn.tsolve.identity.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.exception.UserListException;

public record UserListQuery(String q, Role role, String status, Long departmentId, Pageable pageable) {
    public static UserListQuery parse(MultiValueMap<String, String> parameters) {
        String q = single(parameters, "q");
        q = q == null ? null : q.trim();
        if (q != null && q.length() > 100) {
            throw new UserListException(
                    HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", "Search", "max_length", 100), "q");
        }
        try {
            int page = integer(single(parameters, "page"), 0);
            int size = integer(single(parameters, "size"), 20);
            if (page < 0 || size < 1 || size > 100) {
                throw invalid();
            }
            String roleValue = single(parameters, "role");
            Role role = roleValue == null ? null : Role.valueOf(roleValue);
            String status = single(parameters, "status");
            if (status != null && !List.of("ACTIVE", "DEACTIVATED").contains(status)) {
                throw invalid();
            }
            String department = single(parameters, "departmentId");
            Long departmentId = department == null ? null : positiveId(department);
            var orders = new ArrayList<Sort.Order>();
            for (String expression : parameters.getOrDefault("sort", List.of("fullName,asc"))) {
                String[] pieces = expression.split(",", -1);
                if (pieces.length != 2
                        || !List.of("fullName", "email").contains(pieces[0])
                        || !List.of("asc", "desc").contains(pieces[1])) {
                    throw invalid();
                }
                orders.add(new Sort.Order(Sort.Direction.fromString(pieces[1]), pieces[0]));
            }
            return new UserListQuery(
                    q == null || q.isEmpty() ? null : q,
                    role,
                    status,
                    departmentId,
                    PageRequest.of(page, size, Sort.by(orders)));
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
    }

    private static String single(MultiValueMap<String, String> parameters, String key) {
        List<String> values = parameters.get(key);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw invalid();
        }
        return values.getFirst();
    }

    private static int integer(String value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static long positiveId(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) {
            throw invalid();
        }
        return Long.parseLong(value);
    }

    private static UserListException invalid() {
        return new UserListException(HttpStatus.BAD_REQUEST, "MSG06");
    }
}
