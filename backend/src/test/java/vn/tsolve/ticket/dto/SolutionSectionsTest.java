package vn.tsolve.ticket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SolutionSectionsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void writesOnlyTheFiveSections_BUG_214() throws Exception {
        SolutionSections sections = new SolutionSections("Drops", "Old", "Update", "", "Laptops");

        JsonNode written = json.valueToTree(sections);

        assertThat(written.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability");
        assertThat(json.treeToValue(written, SolutionSections.class)).isEqualTo(sections);
    }

    @Test
    void treatsMissingSectionsAsEmptyText_AC_002_5() {
        SolutionSections sections = new SolutionSections(null, " ", null, null, "");

        assertThat(sections.symptoms()).isEmpty();
        assertThat(sections.length()).isEqualTo(1);
    }
}
