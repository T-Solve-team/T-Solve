package vn.tsolve.tracker.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdfTextExtractorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void joinsTheTextOfEachBlockWithANewLine_AC_011_1() throws Exception {
        String adf =
                """
                {"type": "doc", "version": 1, "content": [
                  {"type": "paragraph", "content": [
                    {"type": "text", "text": "VPN drops "},
                    {"type": "text", "text": "every hour", "marks": [{"type": "strong"}]}]},
                  {"type": "bulletList", "content": [
                    {"type": "listItem", "content": [{"type": "paragraph", "content": [
                      {"type": "text", "text": "Windows 11"}]}]},
                    {"type": "listItem", "content": [{"type": "paragraph", "content": [
                      {"type": "text", "text": "after sleep"}, {"type": "hardBreak"},
                      {"type": "text", "text": "and on Wi-Fi"}]}]}]}]}
                """;

        assertThat(AdfTextExtractor.toText(JSON.readTree(adf)))
                .isEqualTo("VPN drops every hour\nWindows 11\nafter sleep\nand on Wi-Fi");
    }

    @Test
    void givesAnEmptyStringForNoDescriptionOrOneWithoutText_AC_011_1() throws Exception {
        String mediaOnly =
                """
                {"type": "doc", "version": 1, "content": [
                  {"type": "mediaSingle", "content": [{"type": "media", "attrs": {"id": "abc"}}]}]}
                """;

        assertThat(AdfTextExtractor.toText(null)).isEmpty();
        assertThat(AdfTextExtractor.toText(JSON.nullNode())).isEmpty();
        assertThat(AdfTextExtractor.toText(JSON.readTree(mediaOnly))).isEmpty();
    }

    @Test
    void readsAPlainStringDescriptionAsIs_AC_011_1() {
        assertThat(AdfTextExtractor.toText(JSON.getNodeFactory().textNode("  Reconnect fails.  ")))
                .isEqualTo("Reconnect fails.");
    }
}
