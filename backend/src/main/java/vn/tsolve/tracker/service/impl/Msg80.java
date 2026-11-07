package vn.tsolve.tracker.service.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of the MSG80 comment (SRS System Messages) as paragraphs, one per line of the suggestion. It leaves out
 * MSG80's "Related documents" sentence and names no source (AC-012.1, AC-012.2; user, 2026-11-07).
 */
final class Msg80 {

    /** What a posted MSG80 comment starts with; used to find one whose id was not stored (AC-012.5). */
    static final String PREFIX = "T-Solve suggestion:";

    static final String QUESTION = "Did this solve your problem? Answer in the T-Solve panel of this issue.";

    /** Jira Cloud's comment limit is 32,767 characters; the paragraphs stay below it (edge case 57). */
    static final int MAX_LENGTH = 32_000;

    private static final String ELLIPSIS = "…";

    private Msg80() {}

    static List<String> paragraphs(String suggestedSolution) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(PREFIX);
        int room = MAX_LENGTH - PREFIX.length() - QUESTION.length();
        for (String line : suggestedSolution.split("\\R")) {
            String text = line.strip();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > room) {
                if (room > ELLIPSIS.length()) {
                    paragraphs.add(text.substring(0, room - ELLIPSIS.length()) + ELLIPSIS);
                }
                break;
            }
            paragraphs.add(text);
            room -= text.length();
        }
        paragraphs.add(QUESTION);
        return paragraphs;
    }
}
