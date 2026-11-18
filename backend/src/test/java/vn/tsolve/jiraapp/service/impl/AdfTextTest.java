package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdfTextTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void putsBlocksOnTheirOwnLinesAndDropsTheTrailingBreaks_AC_005_7() throws Exception {
        String adf = """
                {"type": "doc", "version": 1, "content": [
                  {"type": "paragraph", "content": [{"type": "text", "text": "Reconnect fails."},
                                                    {"type": "hardBreak"}, {"type": "text", "text": "Every hour."}]},
                  {"type": "bulletList", "content": [
                    {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "A"}]}]}]}]}
                """;

        assertThat(AdfText.issueText(json.readTree(adf))).isEqualTo("Reconnect fails.\nEvery hour.\nA");
    }

    @Test
    void readsNothingFromAMissingDescription_AC_005_7() {
        assertThat(AdfText.issueText(null)).isEmpty();
        assertThat(AdfText.issueText(json.nullNode())).isEmpty();
    }
}
