package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Msg80Test {

    @Test
    void wrapsTheSuggestionInMsg80WithoutRelatedDocuments_AC_012_1() {
        assertThat(Msg80.paragraphs("Update the VPN client to 5.2."))
                .containsExactly(
                        "T-Solve suggestion:",
                        "Update the VPN client to 5.2.",
                        "Did this solve your problem? Answer in the T-Solve panel of this issue.");
    }

    @Test
    void namesNoSource_AC_012_2() {
        assertThat(String.join("\n", Msg80.paragraphs("Restart the VPN service.")))
                .doesNotContain("Related documents");
    }

    @Test
    void keepsEachLineOfTheSuggestionAsItsOwnParagraph_EDGE_57() {
        assertThat(Msg80.paragraphs("1. Update the client.\n\n2. Turn off Wi-Fi power saving.  \r\n"))
                .containsExactly(
                        "T-Solve suggestion:",
                        "1. Update the client.",
                        "2. Turn off Wi-Fi power saving.",
                        "Did this solve your problem? Answer in the T-Solve panel of this issue.");
    }

    @Test
    void cutsASuggestionLongerThanJiraAcceptsWithAnEllipsis_EDGE_57() {
        List<String> paragraphs = Msg80.paragraphs("x".repeat(40_000));

        int total = paragraphs.stream().mapToInt(String::length).sum();
        assertThat(total).isLessThanOrEqualTo(Msg80.MAX_LENGTH);
        assertThat(paragraphs.get(1)).endsWith("…");
        assertThat(paragraphs.get(paragraphs.size() - 1))
                .isEqualTo("Did this solve your problem? Answer in the T-Solve panel of this issue.");
    }

    @Test
    void startsWithThePrefixUsedToFindAPostedComment_AC_012_5() {
        assertThat(String.join("\n", Msg80.paragraphs("Restart."))).startsWith(Msg80.PREFIX);
    }
}
