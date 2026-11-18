package vn.tsolve.jiraapp.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;
import vn.tsolve.ticket.dto.OpenDraft;

/**
 * The subject, plain-text body and HTML body of one writer's Draft reminder (jira-app AC-011.3): issue keys, dates and
 * Jira links only, never Solution text. The HTML follows the layout of ticket-review's review emails (KAN-333).
 */
public record DraftReminderMessage(String subject, String body, String html) {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE);
    static final String CLOSING = "Open the issue in Jira and use Continue draft in the T-Solve panel to submit it.";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private static final String TEMPLATE = template();

    /**
     * `drafts` newest first, as the query returns them; `jiraSiteUrl` may be blank, then the rows carry no link;
     * `fullName` may be blank, then the greeting is "Hi there,".
     */
    public static DraftReminderMessage of(List<OpenDraft> drafts, String jiraSiteUrl, String fullName) {
        String subject = drafts.size() == 1
                ? "You have 1 unsubmitted Solution draft in T-Solve"
                : "You have " + drafts.size() + " unsubmitted Solution drafts in T-Solve";
        String site = jiraSiteUrl == null ? "" : jiraSiteUrl.strip().replaceAll("/+$", "");
        StringBuilder body = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (OpenDraft draft : drafts) {
            String day = DAY.format(draft.updatedAt());
            String link = site.isEmpty() ? "" : site + "/browse/" + draft.issueKey();
            body.append("- ").append(draft.issueKey()).append(", last saved ").append(day);
            if (!link.isEmpty()) {
                body.append(": ").append(link);
            }
            body.append('\n');
            rows.append(row(draft.issueKey(), link, day));
        }
        body.append('\n').append(CLOSING).append('\n');
        String greeting = "Hi " + (fullName == null || fullName.isBlank() ? "there" : fullName.strip()) + ",";
        String html = fill(Map.of(
                "heading", escape(subject),
                "greeting", escape(greeting),
                "rows", rows.toString(),
                "closing", escape(CLOSING)));
        return new DraftReminderMessage(subject, body.toString(), html);
    }

    private static String row(String issueKey, String link, String day) {
        String key = escape(issueKey);
        String keyCell = link.isEmpty()
                ? key
                : "<a href=\"" + escape(link) + "\" style=\"color:#5577FF;text-decoration:none;\">" + key + "</a>";
        return "        <tr>\n"
                + "          <td style=\"padding:10px 20px;border-top:1px solid #e6e9f0;"
                + "font:600 15px/1.45 Arial,sans-serif;color:#141a26;\">" + keyCell + "</td>\n"
                + "          <td style=\"padding:10px 20px;border-top:1px solid #e6e9f0;"
                + "font:14px/1.45 Arial,sans-serif;color:#6b7383;text-align:right;\">" + escape(day) + "</td>\n"
                + "        </tr>\n";
    }

    // One pass, so a value that happens to contain "{{...}}" stays literal text.
    private static String fill(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(TEMPLATE);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String template() {
        try {
            return new ClassPathResource("mail/draft-reminder.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Draft reminder email template is unavailable", exception);
        }
    }
}
