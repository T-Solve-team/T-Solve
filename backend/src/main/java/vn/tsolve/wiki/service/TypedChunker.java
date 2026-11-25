package vn.tsolve.wiki.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.tsolve.wiki.entity.WikiDocumentType;

@Component
public class TypedChunker {
    private static final int MAX_CHARACTERS = 1500;
    private static final Pattern ATX_HEADING = Pattern.compile("^ {0,3}(#{1,6})(?:\\h+|$)(.*)");
    private static final Pattern SETEXT_HEADING = Pattern.compile("^ {0,3}(=+|-+)\\h*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(\\x60{3,}|~{3,})(.*)");
    private static final Pattern POLICY_ARTICLE =
            // Plain or bold only: a list item that cites an article stays with its article (AC-007.4).
            Pattern.compile("^(?:\\*\\*|__)?(?:Điều|Article)\\h+\\d+\\b");
    private static final Pattern FAQ_QUESTION = Pattern.compile("^(?:Q:|Hỏi:|Câu hỏi\\b)");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile(":?-+:?");

    public List<String> split(WikiDocumentType type, String title, String markdown) {
        if (type == WikiDocumentType.OTHER) throw new IllegalArgumentException("OTHER uses plain text");
        var lines = markdown.lines().toList();
        var headings = new ArrayList<Heading>();
        var passages = new ArrayList<String>();
        var body = new StringBuilder();
        var prefixes = new TreeMap<Integer, String>();
        boolean wholeDocument = type == WikiDocumentType.ANNOUNCEMENT || type == WikiDocumentType.FORM;
        String prefix = PassageSplitter.prefix(type, title, List.of());
        prefixes.put(0, prefix);
        boolean hasText = false;
        boolean previousQuestion = false;
        String fence = "";

        for (int i = 0; i < lines.size() && passages.size() < PassageSplitter.MAX_PASSAGES; i++) {
            String line = lines.get(i);
            if (!fence.isEmpty()) {
                var end = FENCE.matcher(line);
                if (end.matches()
                        && end.group(1).startsWith(fence)
                        && end.group(2).isBlank()) fence = "";
                appendLine(body, line);
                hasText |= !line.isBlank();
                continue;
            }
            var opening = FENCE.matcher(line);
            if (opening.matches()) {
                fence = opening.group(1);
                appendLine(body, line);
                previousQuestion = false;
                continue;
            }

            Heading heading = heading(lines, i);
            if (heading != null) {
                if (!wholeDocument) flush(passages, body, prefix);
                if (wholeDocument) appendLine(body, line);
                if (heading.setext()) {
                    i++;
                    if (wholeDocument) appendLine(body, lines.get(i));
                }
                headings.removeIf(parent -> parent.level() >= heading.level());
                if (!heading.title().isEmpty()) headings.add(heading);
                prefix = PassageSplitter.prefix(
                        type, title, headings.stream().map(Heading::title).toList());
                if (wholeDocument) prefixes.put(body.length(), prefix);
                previousQuestion = line.stripTrailing().endsWith("?");
                continue;
            }

            if (type == WikiDocumentType.REFERENCE && i + 1 < lines.size()) {
                List<String> headers = cells(line);
                List<String> separator = cells(lines.get(i + 1));
                if (!headers.isEmpty()
                        && headers.size() == separator.size()
                        && separator.stream()
                                .allMatch(cell -> TABLE_SEPARATOR.matcher(cell).matches())) {
                    flush(passages, body, prefix);
                    i++;
                    while (i + 1 < lines.size() && passages.size() < PassageSplitter.MAX_PASSAGES) {
                        List<String> row = cells(lines.get(i + 1));
                        if (row.isEmpty()) break;
                        i++;
                        splitUnit(passages, tableRow(headers, row), prefix);
                    }
                    continue;
                }
            }

            String trimmed = line.strip();
            boolean question = trimmed.endsWith("?");
            if ((type == WikiDocumentType.POLICY
                            && POLICY_ARTICLE.matcher(trimmed).find())
                    || (type == WikiDocumentType.FAQ
                            && (FAQ_QUESTION.matcher(trimmed).find() || (question && !previousQuestion)))) {
                flush(passages, body, prefix);
            }
            appendLine(body, line);
            hasText |= !trimmed.isEmpty();
            previousQuestion = question;
        }

        if (wholeDocument) {
            if (hasText) {
                PassageSplitter.append(
                        passages, body.toString(), MAX_CHARACTERS, true, offset -> prefixes.floorEntry(offset)
                                .getValue());
            }
        } else {
            flush(passages, body, prefix);
        }
        return List.copyOf(passages);
    }

    private static Heading heading(List<String> lines, int index) {
        var line = lines.get(index);
        var atx = ATX_HEADING.matcher(line);
        if (atx.matches()) {
            return new Heading(
                    atx.group(1).length(),
                    atx.group(2).replaceFirst("(?:^|\\h+)#+\\h*$", "").strip(),
                    false);
        }
        if (line.isBlank() || index + 1 == lines.size()) return null;
        var setext = SETEXT_HEADING.matcher(lines.get(index + 1));
        if (!setext.matches()) return null;
        return new Heading(setext.group(1).startsWith("=") ? 1 : 2, line.strip(), true);
    }

    private static List<String> cells(String line) {
        if (!line.contains("|")) return List.of();
        var cells = new ArrayList<String>();
        var cell = new StringBuilder();
        String trimmed = line.strip();
        boolean foundPipe = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (current == '\\' && i + 1 < trimmed.length()) {
                char next = trimmed.charAt(i + 1);
                if (next == '|' || next == '\\') {
                    if (next == '\\') cell.append(current);
                    cell.append(next);
                    i++;
                    continue;
                }
            }
            if (current == '|') {
                foundPipe = true;
                if (i > 0) cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        if (!foundPipe) return List.of();
        if (!cell.isEmpty() || !trimmed.endsWith("|")) cells.add(cell.toString().strip());
        return cells;
    }

    private static String tableRow(List<String> headers, List<String> row) {
        var pairs = new ArrayList<String>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).isEmpty() ? "Column " + (i + 1) : headers.get(i);
            pairs.add(header + ": " + (i < row.size() ? row.get(i) : ""));
        }
        return String.join("; ", pairs);
    }

    private static void appendLine(StringBuilder body, String line) {
        body.append(line).append('\n');
    }

    private static void flush(List<String> passages, StringBuilder body, String prefix) {
        if (!body.isEmpty()) {
            splitUnit(passages, body.toString(), prefix);
            body.setLength(0);
        }
    }

    private static void splitUnit(List<String> passages, String body, String prefix) {
        PassageSplitter.append(passages, body, MAX_CHARACTERS, true, offset -> prefix);
    }

    private record Heading(int level, String title, boolean setext) {}
}
