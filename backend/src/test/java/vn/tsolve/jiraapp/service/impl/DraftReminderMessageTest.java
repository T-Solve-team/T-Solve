package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.ticket.dto.OpenDraft;

class DraftReminderMessageTest {

    private static final UUID WRITER = new UUID(336, 1);
    // 2026-11-13 23:30 UTC is already 2026-11-14 in Asia/Ho_Chi_Minh.
    private static final OpenDraft NEWER = new OpenDraft(WRITER, "SAM1-16", Instant.parse("2026-11-13T23:30:00Z"));
    private static final OpenDraft OLDER = new OpenDraft(WRITER, "SAM1-9", Instant.parse("2026-11-10T02:00:00Z"));

    @Test
    void usesTheSingularSubjectForOneOpenDraft_AC_011_3() {
        DraftReminderMessage message = DraftReminderMessage.of(List.of(NEWER), "https://acme.atlassian.net", "Ngo Bao");

        assertThat(message.subject()).isEqualTo("You have 1 unsubmitted Solution draft in T-Solve");
    }

    @Test
    void listsEveryOpenDraftWithItsVietnamDateAndJiraLink_AC_011_3() {
        DraftReminderMessage message =
                DraftReminderMessage.of(List.of(NEWER, OLDER), "https://acme.atlassian.net/", "Ngo Bao");

        assertThat(message.subject()).isEqualTo("You have 2 unsubmitted Solution drafts in T-Solve");
        assertThat(message.body()).isEqualTo("""
                        - SAM1-16, last saved 2026-11-14: https://acme.atlassian.net/browse/SAM1-16
                        - SAM1-9, last saved 2026-11-10: https://acme.atlassian.net/browse/SAM1-9

                        Open the issue in Jira and use Continue draft in the T-Solve panel to submit it.
                        """);
    }

    @Test
    void leavesOutTheLinkWithoutAJiraSiteUrl_AC_011_3() {
        DraftReminderMessage message = DraftReminderMessage.of(List.of(OLDER), " ", "Ngo Bao");

        assertThat(message.body())
                .startsWith("- SAM1-9, last saved 2026-11-10\n")
                .doesNotContain("http");
        assertThat(message.html()).contains(">SAM1-9</td>").doesNotContain("href=");
    }

    @Test
    void rendersTheHtmlBodyInTheReviewEmailLayout_AC_011_3() {
        DraftReminderMessage message =
                DraftReminderMessage.of(List.of(NEWER, OLDER), "https://acme.atlassian.net", "Ngo Bao");

        assertThat(message.html())
                .startsWith("<!doctype html>")
                .contains(
                        "Draft reminder",
                        "<h1",
                        "You have 2 unsubmitted Solution drafts in T-Solve",
                        "Hi Ngo Bao,",
                        "href=\"https://acme.atlassian.net/browse/SAM1-16\"",
                        "2026-11-14",
                        "Open the issue in Jira and use Continue draft in the T-Solve panel to submit it.",
                        "You got this email because you have unsubmitted Solution drafts on T-Solve.",
                        "This is an automated message")
                .doesNotContain("{{");
        // Newest saved first, as in the plain-text body.
        assertThat(message.html().indexOf("SAM1-16")).isLessThan(message.html().indexOf("SAM1-9"));
    }

    @Test
    void greetsWithoutANameWhenTheFullNameIsBlank_AC_011_3() {
        DraftReminderMessage message = DraftReminderMessage.of(List.of(NEWER), "", " ");

        assertThat(message.html()).contains("Hi there,");
    }

    @Test
    void escapesTheNameAndKeepsTemplateSyntaxLiteral_AC_011_3() {
        DraftReminderMessage message = DraftReminderMessage.of(List.of(NEWER), "", "<b>Bao</b> {{rows}}");

        assertThat(message.html())
                .contains("Hi &lt;b&gt;Bao&lt;/b&gt; {{rows}},")
                .doesNotContain("<b>Bao</b>");
    }
}
