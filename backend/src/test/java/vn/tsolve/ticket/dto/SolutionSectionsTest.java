package vn.tsolve.ticket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SolutionSectionsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void writesOnlyTheFiveSections_BUG_198() throws Exception {
        SolutionSections sections = new SolutionSections("Drops", "Old", "Update", "", "Laptops");

        JsonNode written = json.valueToTree(sections);

        assertThat(written.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability");
        assertThat(json.treeToValue(written, SolutionSections.class)).isEqualTo(sections);
    }

    @Test
    void treatsMissingSectionsAsEmpty_AC_006_2() {
        SolutionSections sections = new SolutionSections(null, " ", null, null, "");

        assertThat(sections.isEmpty()).isTrue();
        assertThat(sections.length()).isEqualTo(1);
    }
}
