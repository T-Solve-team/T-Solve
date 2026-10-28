package vn.tsolve.ticket.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** NFR 1: a 10 MB file of about 5,000 rows is checked well inside the 60 s preview budget (edge case 27). */
class CsvImportPerformanceTest {

    private static final int ROWS = 5_000;

    /** Multilingual text with the characters that make CSV hard: commas, quotes and line breaks. */
    private static String text(int i, int repeat) {
        String chunk = "Lỗi đăng nhập, \"session\" hết hạn 😀 user " + i + "\nKhởi động lại dịch vụ; Neustart, 再起動. ";
        return chunk.repeat(repeat);
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static byte[] tenMegabyteFile() {
        StringBuilder csv = new StringBuilder(CsvColumns.TEMPLATE.size() * 20);
        csv.append(String.join(",", CsvColumns.TEMPLATE)).append("\r\n");
        for (int i = 0; i < ROWS; i++) {
            csv.append("PERF-")
                    .append(i)
                    .append(',')
                    .append(quoted("Ticket " + i + ", login fails"))
                    .append(',')
                    .append(quoted(text(i, 4)))
                    .append(",Nguyễn Văn An,2026-09-30,")
                    .append(quoted(text(i, 5)))
                    .append(',')
                    .append(quoted(text(i, 3)))
                    .append(',')
                    .append(quoted(text(i, 4)))
                    .append(',')
                    .append(quoted("none"))
                    .append(',')
                    .append(quoted("All users"))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void checksATenMegabyteFileWellWithinSixtySeconds_AC_002_1() {
        byte[] file = tenMegabyteFile();
        assertThat(file.length).isBetween(9_000_000, 10 * 1024 * 1024);

        long start = System.nanoTime();
        ParsedCsv csv = new CsvTicketParser().parse(new ByteArrayInputStream(file));
        List<CsvRowValidator.Result> results =
                new CsvRowValidator(Clock.systemUTC()).validate(csv.rows(), Set.of("PERF-7"));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(results).hasSize(ROWS);
        assertThat(results.stream().filter(r -> !r.errors().isEmpty())).hasSize(1);
        // ponytail: wall-clock assert; 10 s leaves room on a slow CI box while the NFR budget is 60 s
        assertThat(took).isLessThan(Duration.ofSeconds(10));
    }
}
