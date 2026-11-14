package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.tsolve.worker.client.ReviewNotificationSender.Decision;

class ReviewEmailRendererTest {
    private final ReviewEmailRenderer renderer = new ReviewEmailRenderer();

    static ReviewEmail email(Decision decision, String host, String writer, String title, String feedback) {
        return new ReviewEmail(
                decision,
                "ITS-1",
                title,
                "ITS",
                "reviewer@example.test",
                writer,
                Instant.parse("2026-11-14T18:42:00Z"),
                feedback,
                host == null || host.isBlank()
                        ? null
                        : "https://" + host + "/tickets/00000000-0000-0000-0000-000000000001",
                host);
    }

    @ParameterizedTest
    @EnumSource(Decision.class)
    void rendersDecisionBadgeHeadingAndButton_AC_010_7_BUG_212(Decision decision) {
        var email = email(decision, "acme.example.test", "Linh", "Login fails", "Add evidence");
        String html = renderer.html(email);
        assertThat(html)
                .contains(
                        "Hi Linh,",
                        "ITS-1",
                        "ITS",
                        "Login fails",
                        "reviewer@example.test",
                        "15 Nov 2026, 01:42 (GMT+7)",
                        "acme.example.test",
                        "#5577FF");
        if (decision == Decision.REJECT) {
            assertThat(html)
                    .contains(
                            "Rejected",
                            "Your ticket was rejected",
                            "Rejection reason",
                            ">Open ticket</a>",
                            "#FFF2F0",
                            "#CF1322");
        } else {
            assertThat(html)
                    .contains(
                            "Changes requested",
                            "Your ticket needs a few changes",
                            "Review comment",
                            "Update ticket",
                            "#FFFBE6",
                            "#AD6800");
        }
        assertThat(renderer.plain(email))
                .contains(
                        "Hi Linh,",
                        "ITS-1",
                        "ITS",
                        "Login fails",
                        "reviewer@example.test",
                        "15 Nov 2026, 01:42 (GMT+7)",
                        "Add evidence",
                        email.ticketUrl());
        assertThat(renderer.plain(email))
                .contains((decision == Decision.REJECT ? "Open ticket" : "Update ticket") + ": " + email.ticketUrl());
    }

    @Test
    void omitsProjectSeparatorWhenProjectNameBlank_AC_010_7_BUG_213() {
        for (String project : new String[] {null, "", " "}) {
            var email = new ReviewEmail(
                    Decision.REJECT,
                    "ITS-1",
                    "Login fails",
                    project,
                    "Reviewer",
                    "Writer",
                    Instant.parse("2026-11-14T18:42:00Z"),
                    "Evidence",
                    null,
                    null);
            assertThat(renderer.html(email)).contains(">ITS-1</div>").doesNotContain("ITS-1 ·");
            assertThat(renderer.plain(email)).contains("\nITS-1\nLogin fails\n").doesNotContain("ITS-1 ·");
        }
    }

    @Test
    void fallsBackToHiThereAndReviewerEmail_AC_010_7() {
        var email = email(Decision.REJECT, null, null, "Login fails", "Evidence");
        assertThat(renderer.html(email)).contains("Hi there,", "reviewer@example.test");
        assertThat(renderer.plain(email)).contains("Hi there,", "reviewer@example.test");
    }

    @Test
    void escapesStoredValuesAndPreservesLineBreaks_AC_010_9() {
        var email = new ReviewEmail(
                Decision.REJECT,
                "<key>",
                "<script>title</script>",
                "<project>",
                "<reviewer>",
                "<writer>",
                Instant.parse("2026-11-14T18:42:00Z"),
                "<script>reason</script>\r\nsecond\nthird\rfourth",
                "https://example.test/?a=1&b=2",
                "<workspace>");
        assertThat(renderer.html(email))
                .contains(
                        "&lt;script&gt;title&lt;/script&gt;",
                        "&lt;script&gt;reason&lt;/script&gt;<br>second<br>third<br>fourth",
                        "&lt;key&gt;",
                        "&lt;project&gt;",
                        "&lt;reviewer&gt;",
                        "&lt;writer&gt;",
                        "&lt;workspace&gt;",
                        "a=1&amp;b=2")
                .doesNotContain("<script>");
        assertThat(renderer.plain(email)).contains("<script>reason</script>\r\nsecond\nthird\rfourth");
    }

    @Test
    void omitsButtonLinkAndWorkspaceWhenHostMissing_AC_010_8() {
        for (String host : new String[] {null, "", " "}) {
            var email = email(Decision.REQUEST_CHANGES, host, " ", "Login fails", "Evidence");
            assertThat(renderer.html(email))
                    .doesNotContain("<a ", "https://", "in the", "Update ticket")
                    .contains("Hi there,");
            assertThat(renderer.plain(email)).doesNotContain("https://", "in the", "Update ticket");
        }
    }

    @Test
    void formatsVietnamTimeAcrossUtcDateBoundary_AC_010_7() {
        for (String[] boundary : new String[][] {
            {"2026-11-14T16:59:00Z", "14 Nov 2026, 23:59 (GMT+7)"},
            {"2026-11-14T17:00:00Z", "15 Nov 2026, 00:00 (GMT+7)"}
        }) {
            var email = new ReviewEmail(
                    Decision.REJECT,
                    "ITS-1",
                    "Title",
                    "ITS",
                    "Reviewer",
                    "Writer",
                    Instant.parse(boundary[0]),
                    "Reason",
                    null,
                    null);
            assertThat(renderer.html(email)).contains(boundary[1]);
            assertThat(renderer.plain(email)).contains(boundary[1]);
        }
    }
}
