package vn.tsolve.jiraapp.service.impl;

import vn.tsolve.ticket.dto.SolutionSections;

/**
 * The issue text the Solution Form opens with when there is no Draft, and the Draft made when an issue moves to Done:
 * the summary, a blank line and the description in Symptoms, the other sections empty (jira-app AC-002.1, AC-005.7).
 */
final class IssueText {

    /** The five sections together may hold this many characters (AC-002.4). */
    static final int MAX_SOLUTION_LENGTH = 10_000;

    private IssueText() {}

    static SolutionSections sections(String summary, String description) {
        String text = join(summary, description);
        if (text.codePointCount(0, text.length()) > MAX_SOLUTION_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_SOLUTION_LENGTH));
        }
        return new SolutionSections(text, "", "", "", "");
    }

    private static String join(String summary, String description) {
        String first = summary == null ? "" : summary.strip();
        String second = description == null ? "" : description.strip();
        if (first.isEmpty() || second.isEmpty()) {
            return first + second;
        }
        return first + "\n\n" + second;
    }
}
