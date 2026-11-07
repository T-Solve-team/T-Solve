package vn.tsolve.identity.dto;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;

public record UserListQuery(
        String q,
        Role role,
        AccountStatus status,
        UUID departmentId,
        int page,
        int size,
        String sortField,
        boolean descending) {

    public static UserListQuery of(
            String q,
            Role role,
            AccountStatus status,
            UUID departmentId,
            String page,
            String size,
            List<String> sorts) {
        String search = q == null ? "" : q.trim();
        if (search.length() > 100) {
            throw new SearchTooLongException();
        }
        String field = "fullName";
        boolean descending = false;
        for (String sort : sorts) {
            String[] parts = sort.split(",", -1);
            if (parts.length == 2
                    && List.of("fullName", "email", "lastSignInAt").contains(parts[0])
                    && List.of("asc", "desc").contains(parts[1])) {
                field = parts[0];
                descending = parts[1].equals("desc");
                break;
            }
        }
        return new UserListQuery(
                search,
                role,
                status,
                departmentId,
                Math.max(0, integer(page, 0)),
                Math.clamp(integer(size, 20), 1, 100),
                field,
                descending);
    }

    public String pattern() {
        return "%"
                + q.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class SearchTooLongException extends MessageCodeException {
        private SearchTooLongException() {
            super(HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", "Search", "max_length", 100));
        }
    }
}
