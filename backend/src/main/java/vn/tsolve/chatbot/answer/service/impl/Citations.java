package vn.tsolve.chatbot.answer.service.impl;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;

/** The model's text with unknown {@code [n]} markers removed, and the kept sources it cites, in number order. */
record Citations(String text, List<ChatbotCitation> citations) {
    // Only a single integer 1..99 is a marker: [01], [ 1 ], [0] and [n=1] stay plain text. Right after a letter,
    // digit or underscore it is indexing in code (args[1]), not a marker (BUG-185); [1][2] still counts as two.
    // A group [1,2] or [3, 5] is first rewritten as [1][2] / [3][5] (AC-006.8, BUG-348).
    private static final Pattern GROUP = Pattern.compile("(?<![\\p{L}\\p{N}_])\\[([1-9]\\d?(?:, ?[1-9]\\d?)+)\\]");
    private static final Pattern MARKER = Pattern.compile("(?<![\\p{L}\\p{N}_])\\[([1-9]\\d?)\\]");
    private static final String WORD_END = " \n.,;:!?)";
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    /**
     * AC-005.5: the text cites a kept source and has a letter or digit outside its markers, so "[2]" or "[1][2]." alone
     * is no answer (BUG-246, BUG-364).
     */
    boolean answers() {
        return !citations.isEmpty()
                && LETTER_OR_DIGIT.matcher(MARKER.matcher(text).replaceAll("")).find();
    }

    static Citations of(String text, List<ChatbotSource> kept) {
        text = GROUP.matcher(text).replaceAll(group -> "[" + group.group(1).replaceAll(", ?", "][") + "]");
        var cited = new TreeSet<Integer>();
        var result = new StringBuilder();
        var matcher = MARKER.matcher(text);
        int copied = 0;
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (number <= kept.size()) {
                cited.add(number);
                continue;
            }
            result.append(text, copied, matcher.start());
            copied = matcher.end();
            // Drop the space before a removed marker when a space, punctuation or the end follows,
            // so "a [9] b" reads "a b" and "a [9]." reads "a.".
            boolean wordEnds = copied == text.length() || WORD_END.indexOf(text.charAt(copied)) >= 0;
            if (wordEnds && !result.isEmpty() && result.charAt(result.length() - 1) == ' ') {
                result.setLength(result.length() - 1);
            }
        }
        result.append(text, copied, text.length());
        return new Citations(
                result.toString(),
                cited.stream().map(number -> kept.get(number - 1).citation()).toList());
    }
}
