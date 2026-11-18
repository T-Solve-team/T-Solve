package vn.tsolve.wiki.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PassageSplitter {
    private static final int MAX_CHARACTERS = 1000;
    // ponytail: keep only the first 200 passages to bound bge-m3 CPU work per document.
    private static final int MAX_PASSAGES = 200;
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\h*\\R");

    public List<String> split(String content) {
        List<String> passages = new ArrayList<>();
        int start = 0;
        while (start < content.length() && passages.size() < MAX_PASSAGES) {
            if (Character.isWhitespace(content.charAt(start))) {
                start++;
                continue;
            }
            int end = Math.min(start + MAX_CHARACTERS, content.length());
            if (end < content.length()) end = boundary(content, start, end);
            passages.add(content.substring(start, end).stripTrailing());
            start = end;
        }
        return List.copyOf(passages);
    }

    private int boundary(String content, int start, int limit) {
        var breaks = PARAGRAPH_BREAK.matcher(content).region(start, limit);
        int paragraph = -1;
        while (breaks.find()) paragraph = breaks.start();
        if (paragraph > start) return paragraph;
        if (Character.isWhitespace(content.charAt(limit))) return limit;
        for (int index = limit - 1; index > start; index--) {
            if (Character.isWhitespace(content.charAt(index))) return index;
        }
        return limit;
    }
}
