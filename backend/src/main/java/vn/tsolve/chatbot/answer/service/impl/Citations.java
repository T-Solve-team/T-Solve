package vn.tsolve.chatbot.answer.service.impl;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;

/** The model's text with unknown {@code [n]} markers removed, and the kept sources it cites, in number order. */
record Citations(String text, List<ChatbotCitation> citations) {
    // Only a single integer 1..99 is a marker: [01], [ 1 ], [0] and [1, 2] stay plain text.
    private static final Pattern MARKER = Pattern.compile("\\[([1-9]\\d?)\\]");
    private static final String WORD_END = " \n.,;:!?)";

    static Citations of(String text, List<ChatbotSource> kept) {
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
