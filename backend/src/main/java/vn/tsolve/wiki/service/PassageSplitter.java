package vn.tsolve.wiki.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.tsolve.wiki.entity.WikiDocumentType;

@Component
public class PassageSplitter {
    private static final int MAX_CHARACTERS = 1000;
    static final int MAX_PASSAGES = 200;
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\h*\\R");
    private static final Pattern LIST_ITEM = Pattern.compile("(?m)^\\h*(?:[-+*]|\\d{1,9}[.)])\\h+");

    public List<String> split(String content) {
        return split(content, "");
    }

    public List<String> split(WikiDocumentType type, String title, String content) {
        return split(content, prefix(type, title, List.of()));
    }

    private List<String> split(String content, String prefix) {
        List<String> passages = new ArrayList<>();
        append(passages, content, MAX_CHARACTERS, false, offset -> prefix);
        return List.copyOf(passages);
    }

    static String prefix(WikiDocumentType type, String title, List<String> headings) {
        String base = "[" + type.name().toLowerCase(Locale.ROOT) + "] " + title;
        return (headings.isEmpty() ? base : base + " > " + String.join(" > ", headings)) + "\n";
    }

    static void append(
            List<String> passages, String content, int maxCharacters, boolean typed, IntFunction<String> prefixAt) {
        int start = 0;
        while (start < content.length() && passages.size() < MAX_PASSAGES) {
            if (Character.isWhitespace(content.charAt(start))) {
                start++;
                continue;
            }
            String prefix = prefixAt.apply(start);
            int budget = typed ? maxCharacters - prefix.length() : maxCharacters;
            if (budget <= 0) throw new IllegalArgumentException("Heading path exceeds the passage size");
            int end = Math.min(start + budget, content.length());
            if (end < content.length()) {
                end = boundary(content, start, end, typed);
                if (Character.isLowSurrogate(content.charAt(end))) end--;
                if (end == start) throw new IllegalArgumentException("Heading path leaves no space for text");
            }
            passages.add(prefix + content.substring(start, end).stripTrailing());
            start = end;
        }
    }

    private static int boundary(String content, int start, int limit, boolean typed) {
        if (typed) {
            int item = lastBreak(LIST_ITEM, content, start, limit);
            if (item > start) return item;
        }
        int paragraph = lastBreak(PARAGRAPH_BREAK, content, start, limit);
        if (paragraph > start) return paragraph;
        if (Character.isWhitespace(content.charAt(limit))) return limit;
        for (int index = limit - 1; index > start; index--) {
            if (Character.isWhitespace(content.charAt(index))) return index;
        }
        return limit;
    }

    private static int lastBreak(Pattern pattern, String content, int start, int limit) {
        var breaks = pattern.matcher(content).region(start, limit).useAnchoringBounds(false);
        int last = -1;
        while (breaks.find()) last = breaks.start();
        return last;
    }
}
