package vn.tsolve.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tsolve.chatbot.eval.GoldFile.Kind;
import vn.tsolve.chatbot.eval.GoldFile.Source;

class GoldFileTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID = """
            {"question":"  How do I reconnect?  ","expected":[{"kind":"TICKET","id":"%s"}],"groups":{"team":"IT"}}
            """.formatted(ID);

    @TempDir
    Path directory;

    @Test
    void parsesLinesAndGroups_AC_015_2() throws Exception {
        Path file = Files.writeString(directory.resolve("gold.jsonl"), "\n" + VALID + "\n" + VALID);
        var lines = GoldFile.parse(file);

        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst().line()).isEqualTo(2);
        assertThat(lines.getFirst().question()).isEqualTo("  How do I reconnect?  ");
        assertThat(lines.getFirst().expected()).containsExactly(new Source(Kind.TICKET, ID));
        assertThat(lines.getFirst().groups()).isEqualTo(Map.of("team", "IT"));
        assertThat(lines.getLast().line()).isEqualTo(4);
    }

    @Test
    void acceptsByteOrderMark_AC_015_2() throws Exception {
        Path file = Files.writeString(directory.resolve("bom.jsonl"), "﻿" + VALID);

        assertThat(GoldFile.parse(file)).hasSize(1);
    }

    @Test
    void rejectsInvalidLineByNumberOnly_AC_015_5() throws Exception {
        for (String invalid : new String[] {
            "{private text",
            VALID.strip() + " {}",
            VALID.replace("  How do I reconnect?  ", " "),
            VALID.replace("  How do I reconnect?  ", "x".repeat(1001)),
            VALID.replace("\"  How do I reconnect?  \"", "123"),
            VALID.replace("\"TICKET\"", "\"UNKNOWN_SECRET\""),
            VALID.replace(ID.toString(), "0-0-0-0-1"),
            VALID.replace("\"IT\"", "42"),
            VALID.replace("\"IT\"", "null"),
            VALID.replace("\"team\":\"IT\"", "\"team\":{}"),
            VALID.replaceFirst("\\[.*?\\]", "[]"),
            VALID.replace("{\"team\":\"IT\"}", "null")
        }) {
            Path file = Files.writeString(directory.resolve("invalid.jsonl"), VALID + "\n" + invalid);
            assertThatThrownBy(() -> GoldFile.parse(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("line 3: ")
                    .hasMessageNotContaining(invalid.strip())
                    .hasMessageNotContaining("How do I reconnect?")
                    .hasNoCause();
        }
    }

    @Test
    void rejectsEmptyFile_AC_015_5() throws Exception {
        Path file = Files.writeString(directory.resolve("empty.jsonl"), " \n\n");
        assertThatThrownBy(() -> GoldFile.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void exampleFileIsValid_AC_015_8() throws Exception {
        var lines = GoldFile.parse(Path.of("rag-eval", "gold.example.jsonl"));
        assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
        assertThat(lines.stream().flatMap(line -> line.expected().stream()).map(Source::kind))
                .contains(Kind.TICKET, Kind.WIKI);
        assertThat(lines).anyMatch(line -> !line.groups().isEmpty());
    }
}
