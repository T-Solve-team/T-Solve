package vn.tsolve.search.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectIdsTest {
    @ParameterizedTest
    @CsvSource({
        "1, 00000000-0000-0000-0000-000000000001",
        "10, 00000000-0000-0000-0000-00000000000a",
        "255, 00000000-0000-0000-0000-0000000000ff"
    })
    void matchesPostgresLpadToHexMapping_BUG_140(long projectId, String postgresUuid) {
        assertThat(ProjectIds.ticketProjectId(projectId)).isEqualTo(UUID.fromString(postgresUuid));
    }
}
