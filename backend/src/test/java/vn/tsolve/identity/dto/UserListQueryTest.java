package vn.tsolve.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import vn.tsolve.identity.exception.UserListException;

class UserListQueryTest {
    @Test
    void defaultsAndPreservesLongIds_AC_004_10_AC_004_13() {
        var parameters = new LinkedMultiValueMap<String, String>();
        assertThatCode(() -> UserListQuery.parse(parameters)).doesNotThrowAnyException();
        var query = UserListQuery.parse(parameters);
        assertThat(query.pageable().getPageNumber()).isZero();
        assertThat(query.pageable().getPageSize()).isEqualTo(20);
        assertThat(query.pageable().getSort().getOrderFor("fullName").isAscending())
                .isTrue();
        parameters.add("departmentId", "9223372036854775807");
        parameters.add("q", "  Nguyễn  ");
        assertThat(UserListQuery.parse(parameters).departmentId()).isEqualTo(Long.MAX_VALUE);
        assertThat(UserListQuery.parse(parameters).q()).isEqualTo("Nguyễn");
    }

    @Test
    void rejectsInvalidInputWithoutEchoingValues_AC_004_14() {
        for (var entry : List.of(
                List.of("page", "-1"),
                List.of("page", "2147483648"),
                List.of("page", "1.5"),
                List.of("size", "0"),
                List.of("size", "101"),
                List.of("size", ""),
                List.of("departmentId", "01"),
                List.of("departmentId", "0"),
                List.of("departmentId", "9223372036854775808"),
                List.of("departmentId", "not-an-id"),
                List.of("role", "OWNER"),
                List.of("status", "UNKNOWN"),
                List.of("sort", "lastSignInAt,desc"),
                List.of("sort", "fullName,sideways"),
                List.of("sort", "email;drop table app_user,asc"),
                List.of("sort", "email"))) {
            var parameters = new LinkedMultiValueMap<String, String>();
            parameters.add(entry.getFirst(), entry.getLast());
            assertThatThrownBy(() -> UserListQuery.parse(parameters))
                    .as(entry.toString())
                    .isInstanceOf(UserListException.class)
                    .hasMessage("MSG06");
        }
    }

    @Test
    void validatesTrimmedSearchAndMultipleSorts_AC_004_11_AC_004_13_AC_004_14() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("q", "  " + "x".repeat(100) + "  ");
        parameters.add("sort", "email,desc");
        parameters.add("sort", "fullName,asc");
        assertThat(UserListQuery.parse(parameters).q()).hasSize(100);
        assertThat(UserListQuery.parse(parameters).pageable().getSort().stream())
                .hasSize(2);
        parameters.set("q", "x".repeat(101));
        assertThatThrownBy(() -> UserListQuery.parse(parameters)).isInstanceOfSatisfying(UserListException.class, e -> {
            assertThat(e.getCode()).isEqualTo("MSG02");
            assertThat(e.getField()).isEqualTo("q");
            assertThat(e.getParams()).containsEntry("field_name", "Search").containsEntry("max_length", 100);
        });
    }
}
