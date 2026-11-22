package vn.tsolve.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.tsolve.wiki.entity.WikiDocumentType;

class TypedChunkerTest {
    private final TypedChunker chunker = new TypedChunker();

    @ParameterizedTest
    @EnumSource(
            value = WikiDocumentType.class,
            names = {"RUNBOOK", "GUIDE", "SPEC", "RECORD"})
    void usesNearestHeadingSectionAndRetainsItsParents_AC_007_4_AC_007_6(WikiDocumentType type) {
        String prefix = "[" + type.name().toLowerCase(Locale.ROOT) + "] Manual";
        assertThat(chunker.split(type, "Manual", """
                Intro
                # Platform
                ## Linux
                First
                ### Startup
                Second
                ## Windows
                Third
                """))
                .containsExactly(
                        prefix + "\nIntro",
                        prefix + " > Platform > Linux\nFirst",
                        prefix + " > Platform > Linux > Startup\nSecond",
                        prefix + " > Platform > Windows\nThird");
    }

    @Test
    void splitsPolicyArticlesWithinTheHeading_AC_007_4() {
        assertThat(chunker.split(WikiDocumentType.POLICY, "Policy", """
                # Chapter
                Introduction
                Điều 1. Access
                Request access.
                Article 2: Removal
                Revoke access.
                """))
                .containsExactly(
                        "[policy] Policy > Chapter\nIntroduction",
                        "[policy] Policy > Chapter\nĐiều 1. Access\nRequest access.",
                        "[policy] Policy > Chapter\nArticle 2: Removal\nRevoke access.");
    }

    @Test
    void splitsFaqHeadingsAndQuestionMarkers_AC_007_4() {
        assertThat(chunker.split(WikiDocumentType.FAQ, "Help", """
                # Access
                Q: First?
                Answer one.
                Hỏi: Second?
                Answer two.
                Câu hỏi three
                Answer three.
                Fourth?
                Continued question?
                Answer four.
                ## Billing
                Contact Finance.
                """))
                .containsExactly(
                        "[faq] Help > Access\nQ: First?\nAnswer one.",
                        "[faq] Help > Access\nHỏi: Second?\nAnswer two.",
                        "[faq] Help > Access\nCâu hỏi three\nAnswer three.",
                        "[faq] Help > Access\nFourth?\nContinued question?\nAnswer four.",
                        "[faq] Help > Access > Billing\nContact Finance.");
    }

    @Test
    void convertsReferenceTableRowsAndKeepsTextInOrder_AC_007_4() {
        assertThat(chunker.split(WikiDocumentType.REFERENCE, "Catalog", """
                # Codes
                An introduction.
                | Code |   | Description |
                | --- | --- | :---: |
                | A | Shared | Alpha |
                | B | Shared | One \\| two |
                Text after the table.
                """))
                .containsExactly(
                        "[reference] Catalog > Codes\nAn introduction.",
                        "[reference] Catalog > Codes\nCode: A; Column 2: Shared; Description: Alpha",
                        "[reference] Catalog > Codes\nCode: B; Column 2: Shared; Description: One | two",
                        "[reference] Catalog > Codes\nText after the table.");
    }

    @ParameterizedTest
    @EnumSource(
            value = WikiDocumentType.class,
            names = {"ANNOUNCEMENT", "FORM"})
    void keepsTheWholeDocumentAsOneUnit_AC_007_4(WikiDocumentType type) {
        String markdown = "# First\nSome content.\n## Second\nMore content.";
        assertThat(chunker.split(type, "Title", markdown))
                .containsExactly("[" + type.name().toLowerCase(Locale.ROOT) + "] Title\n" + markdown);
    }

    @Test
    void prefersListThenParagraphThenWhitespaceAndRepeatsPrefix_AC_007_5_AC_007_6() {
        String prefix = "[runbook] Manual > Steps\n";
        String item = "1. " + "a ".repeat(460).strip();
        String second = "2. " + "b ".repeat(460).strip();
        assertThat(chunker.split(WikiDocumentType.RUNBOOK, "Manual", "# Steps\n" + item + "\n" + second))
                .containsExactly(prefix + item, prefix + second);

        String paragraph = "c ".repeat(460).strip();
        String next = "d ".repeat(460).strip();
        assertThat(chunker.split(WikiDocumentType.RUNBOOK, "Manual", "# Steps\n" + paragraph + "\n\n" + next))
                .containsExactly(prefix + paragraph, prefix + next);

        String text = "rest ".repeat(400).strip();
        var passages = chunker.split(WikiDocumentType.RUNBOOK, "Manual", "# Steps\n" + text);
        assertThat(passages)
                .hasSize(2)
                .allSatisfy(passage -> assertThat(passage).startsWith(prefix).hasSizeLessThanOrEqualTo(1500));
        assertThat(passages.stream().map(p -> p.substring(prefix.length())).collect(Collectors.joining(" ")))
                .isEqualTo(text);
    }

    @ParameterizedTest
    @EnumSource(
            value = WikiDocumentType.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OTHER"})
    void skipsHeadingOnlyUnits_AC_007_5(WikiDocumentType type) {
        assertThat(chunker.split(type, "Manual", "# Chapter\n## Empty\n\n### Also empty"))
                .isEmpty();
    }

    @Test
    void retainsOnlyTheFirstTwoHundredPassages_AC_007_6() {
        String markdown = IntStream.range(0, 201)
                .mapToObj(i -> "# Heading " + i + "\nText " + i)
                .collect(Collectors.joining("\n"));
        List<String> passages = chunker.split(WikiDocumentType.GUIDE, "Manual", markdown);
        assertThat(passages).hasSize(200);
        assertThat(passages.getFirst()).isEqualTo("[guide] Manual > Heading 0\nText 0");
        assertThat(passages.getLast()).isEqualTo("[guide] Manual > Heading 199\nText 199");
    }
}
