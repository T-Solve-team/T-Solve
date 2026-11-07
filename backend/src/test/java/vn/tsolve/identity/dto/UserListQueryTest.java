package vn.tsolve.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.exception.MessageCodeException;

class UserListQueryTest {
    private UserListQuery query(String q, String page, String size, String... sort) {
        return UserListQuery.of(q, null, null, null, page, size, List.of(sort));
    }

    @Test
    void trimsSearchAndEscapesLiteralWildcards_AC_004_10() {
        assertThat(query("  A%_\\  ", null, null).q()).isEqualTo("A%_\\");
        assertThat(query("  A%_\\  ", null, null).pattern()).isEqualTo("%a\\%\\_\\\\%");
        assertThat(query("   ", null, null).q()).isEmpty();
    }

    @Test
    void rejectsOnlyOver100AfterTrim_AC_004_13() {
        assertThat(query(" " + "x".repeat(100) + " ", null, null).q()).hasSize(100);
        assertThatThrownBy(() -> query(" " + "x".repeat(101) + " ", null, null))
                .isInstanceOfSatisfying(MessageCodeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MSG02");
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getParams())
                            .containsEntry("field_name", "Search")
                            .containsEntry("max_length", 100);
                });
    }

    @Test
    void selectsFirstValidSortAndFallsBack_AC_004_11() {
        for (String field : List.of("fullName", "email", "lastSignInAt")) {
            for (String direction : List.of("asc", "desc")) {
                var parsed = query(null, null, null, "bad,asc", field + "," + direction, "email,asc");
                assertThat(parsed.sortField()).isEqualTo(field);
                assertThat(parsed.descending()).isEqualTo(direction.equals("desc"));
            }
        }
        assertThat(query(null, null, null, "id,asc", "email,drop", "email,asc,extra")
                        .sortField())
                .isEqualTo("fullName");
        assertThat(query(null, null, null).descending()).isFalse();
    }

    @Test
    void defaultsAndClampsPaging_AC_004_12() {
        assertThat(query(null, null, null).page()).isZero();
        assertThat(query(null, null, null).size()).isEqualTo(20);
        assertThat(query(null, "bad", "bad").size()).isEqualTo(20);
        assertThat(query(null, "-3", "0").page()).isZero();
        assertThat(query(null, "2", "-1").size()).isEqualTo(1);
        assertThat(query(null, "2", "500").size()).isEqualTo(100);
    }
}
