package vn.tsolve.jiraapp.service.impl;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import vn.tsolve.ticket.dto.OpenDraft;

/**
 * The subject and plain-text body of one writer's Draft reminder (jira-app AC-011.3): issue keys, dates and Jira
 * links only, never Solution text.
 */
public record DraftReminderMessage(String subject, String body) {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE);
    static final String CLOSING = "Open the issue in Jira and use Continue draft in the T-Solve panel to submit it.";

    /** `drafts` newest first, as the query returns them; `jiraSiteUrl` may be blank, then the lines carry no link. */
    public static DraftReminderMessage of(List<OpenDraft> drafts, String jiraSiteUrl) {
        String subject = drafts.size() == 1
                ? "You have 1 unsubmitted Solution draft in T-Solve"
                : "You have " + drafts.size() + " unsubmitted Solution drafts in T-Solve";
        String site = jiraSiteUrl == null ? "" : jiraSiteUrl.strip().replaceAll("/+$", "");
        StringBuilder body = new StringBuilder();
        for (OpenDraft draft : drafts) {
            body.append("- ").append(draft.issueKey()).append(", last saved ").append(DAY.format(draft.updatedAt()));
            if (!site.isEmpty()) {
                body.append(": ").append(site).append("/browse/").append(draft.issueKey());
            }
            body.append('\n');
        }
        body.append('\n').append(CLOSING).append('\n');
        return new DraftReminderMessage(subject, body.toString());
    }
}
