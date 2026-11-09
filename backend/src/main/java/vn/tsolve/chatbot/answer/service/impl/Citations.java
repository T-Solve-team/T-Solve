package vn.tsolve.chatbot.answer.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;

final class Citations {
    private static final Pattern MARKER = Pattern.compile("\\[([1-9]\\d?)\\]");

    private Citations() {}

    static Result parse(String text, List<ChatbotSource> kept) {
        var sources = new TreeMap<Integer, ChatbotCitation>();
        for (ChatbotSource source : kept) {
            sources.put(source.number(), source.citation());
        }

        String answer = text;
        String previous;
        do {
            previous = answer;
            var cleaned = new StringBuilder();
            var matcher = MARKER.matcher(answer);
            int end = 0;
            while (matcher.find()) {
                cleaned.append(answer, end, matcher.start());
                end = matcher.end();
                if (sources.containsKey(Integer.parseInt(matcher.group(1)))) {
                    cleaned.append(matcher.group());
                } else if (!cleaned.isEmpty()
                        && cleaned.charAt(cleaned.length() - 1) == ' '
                        && end < answer.length()
                        && answer.charAt(end) == ' ') {
                    // Only remove the extra space at the join, preserving the rest of the model's whitespace.
                    end++;
                }
            }
            cleaned.append(answer, end, answer.length());
            answer = cleaned.toString();
        } while (!answer.equals(previous));

        var cited = new HashSet<Integer>();
        var matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            cited.add(Integer.parseInt(matcher.group(1)));
        }
        return new Result(
                answer,
                sources.values().stream()
                        .filter(citation -> cited.contains(citation.number()))
                        .toList());
    }

    record Result(String text, List<ChatbotCitation> citations) {}
}
