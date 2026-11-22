package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.tsolve.ticket.dto.SolutionSections;

class IssueTextTest {

    @Test
    void putsTheSummaryABlankLineAndTheDescriptionInSymptoms_AC_002_1() {
        assertThat(IssueText.sections(" VPN drops ", "After sleep\n"))
                .isEqualTo(new SolutionSections("VPN drops\n\nAfter sleep", "", "", "", ""));
    }

    @Test
    void usesTheSummaryAloneWhenTheDescriptionIsEmpty_AC_002_1() {
        assertThat(IssueText.sections("VPN drops", null)).isEqualTo(new SolutionSections("VPN drops", "", "", "", ""));
        assertThat(IssueText.sections("VPN drops", "  ")).isEqualTo(new SolutionSections("VPN drops", "", "", "", ""));
    }

    @Test
    void cutsAnIssueLongerThanTheSolutionLimit_AC_002_1() {
        SolutionSections sections = IssueText.sections("S", "😀".repeat(12_000));

        assertThat(sections.symptoms().codePointCount(0, sections.symptoms().length()))
                .isEqualTo(IssueText.MAX_SOLUTION_LENGTH);
    }
}
