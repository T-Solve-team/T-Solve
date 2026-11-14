package vn.tsolve.worker.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;
import vn.tsolve.worker.client.ReviewEmail.Decision;

public final class ReviewEmailRenderer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(
                    "d MMM yyyy, HH:mm '(GMT+7)'", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private final String template;

    public ReviewEmailRenderer() {
        try {
            template =
                    new ClassPathResource("mail/review-notification.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Review email template is unavailable", exception);
        }
    }

    public String subject(ReviewEmail email) {
        String decisionSubject =
                switch (email.decision()) {
                    case REJECT -> "Your ticket was rejected";
                    case REQUEST_CHANGES -> "Changes requested for your ticket";
                };
        return ("[" + email.ticketKey() + "] " + decisionSubject)
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    public String html(ReviewEmail email) {
        String selected = template.replaceAll(
                        "(?s)\\{\\{#button}}(.*?)\\{\\{/button}}", hasText(email.ticketUrl()) ? "$1" : "")
                .replaceAll(
                        "(?s)\\{\\{#workspace}}(.*?)\\{\\{/workspace}}", hasText(email.workspaceName()) ? "$1" : "");
        Map<String, String> values = values(email);
        Matcher matcher = PLACEHOLDER.matcher(selected);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = HtmlUtils.htmlEscape(values.get(matcher.group(1)), "UTF-8");
            if (matcher.group(1).equals("feedback")) {
                value = value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
            }
            // Replace once: stored text containing template syntax must remain literal text.
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String plain(ReviewEmail email) {
        var values = values(email);
        return "T-Solve\n\n%s\n%s\n%s %s\n\n%s%s\n%s\nReviewed by %s · %s\n\n%s\n%s\n\n%s%sThis is an automated message — please do not reply."
                .formatted(
                        values.get("badge"),
                        values.get("heading"),
                        values.get("greeting"),
                        values.get("intro"),
                        values.get("ticketKey"),
                        values.get("projectSuffix"),
                        values.get("ticketTitle"),
                        values.get("reviewerName"),
                        values.get("decidedAt"),
                        values.get("feedbackHeading"),
                        values.get("feedback"),
                        hasText(email.ticketUrl()) ? values.get("buttonText") + ": " + email.ticketUrl() + "\n\n" : "",
                        "You got this email because you wrote ticket " + values.get("ticketKey")
                                + (hasText(email.workspaceName())
                                        ? " in the " + email.workspaceName() + " workspace"
                                        : "")
                                + " on T-Solve.\n");
    }

    private static Map<String, String> values(ReviewEmail email) {
        boolean rejected = email.decision() == Decision.REJECT;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("badge", rejected ? "Rejected" : "Changes requested");
        values.put("heading", rejected ? "Your ticket was rejected" : "Your ticket needs a few changes");
        values.put("greeting", "Hi " + (hasText(email.writerName()) ? email.writerName() : "there") + ",");
        values.put(
                "intro",
                rejected
                        ? "a reviewer has rejected your ticket. The reason is below."
                        : "a reviewer asked for changes before your ticket can be approved.");
        values.put("feedbackHeading", rejected ? "Rejection reason" : "Review comment");
        values.put("buttonText", rejected ? "Open ticket" : "Update ticket");
        values.put("badgeBackground", rejected ? "#FFF2F0" : "#FFFBE6");
        values.put("badgeBorder", rejected ? "#FFCCC7" : "#FFE58F");
        values.put("badgeColor", rejected ? "#CF1322" : "#AD6800");
        values.put("feedbackBorder", rejected ? "#FF7875" : "#FFC53D");
        values.put("feedbackBackground", rejected ? "#fffafa" : "#fffdf5");
        values.put("outerPadding", rejected ? "32px" : "40px");
        values.put("ticketKey", text(email.ticketKey()));
        values.put("ticketTitle", text(email.ticketTitle()));
        values.put("projectSuffix", hasText(email.projectName()) ? " · " + email.projectName() : "");
        values.put("reviewerName", text(email.reviewerName()));
        values.put("decidedAt", TIME.format(email.decidedAt()));
        values.put("feedback", text(email.feedback()));
        values.put("ticketUrl", text(email.ticketUrl()));
        values.put("workspaceName", text(email.workspaceName()));
        return values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
