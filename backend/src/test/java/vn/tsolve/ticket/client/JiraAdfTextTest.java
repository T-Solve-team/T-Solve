package vn.tsolve.ticket.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

/** Jira's Atlassian Document Format as the plain text the Import Ticket Preview shows (AC-001.2, design: ADF). */
class JiraAdfTextTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode adf(String content) throws Exception {
        return JSON.readTree("{\"type\":\"doc\",\"version\":1,\"content\":[" + content + "]}");
    }

    @Test
    void joinsTextAndPutsEachParagraphOnItsOwnLine_AC_001_2() throws Exception {
        JsonNode doc = adf("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Login \"},"
                + "{\"type\":\"text\",\"text\":\"fails\",\"marks\":[{\"type\":\"strong\"}]}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"for every user.\"}]}");

        assertThat(AdfText.of(doc)).isEqualTo("Login fails\nfor every user.");
    }

    @Test
    void keepsListItemsHeadingsCodeMentionsAndLineBreaks_AC_001_2() throws Exception {
        JsonNode doc = adf(
                "{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"text\":\"Steps\"}]},"
                        + "{\"type\":\"bulletList\",\"content\":["
                        + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Open the page\"}]}]},"
                        + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                        + "{\"type\":\"text\",\"text\":\"Ask \"},{\"type\":\"mention\",\"attrs\":{\"id\":\"1\",\"text\":\"@Hà\"}}]}]}]},"
                        + "{\"type\":\"codeBlock\",\"content\":[{\"type\":\"text\",\"text\":\"restart gateway\"}]},"
                        + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"hardBreak\"},"
                        + "{\"type\":\"text\",\"text\":\"b\"}]}");

        assertThat(AdfText.of(doc)).isEqualTo("Steps\nOpen the page\nAsk @Hà\nrestart gateway\na\nb");
    }

    @Test
    void readsTableCellsAndSkipsNodesWithoutText_AC_001_2() throws Exception {
        JsonNode doc = adf("{\"type\":\"table\",\"content\":[{\"type\":\"tableRow\",\"content\":["
                + "{\"type\":\"tableHeader\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Host\"}]}]},"
                + "{\"type\":\"tableCell\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"gw-1\"}]}]}]}]},"
                + "{\"type\":\"mediaSingle\",\"content\":[{\"type\":\"media\",\"attrs\":{\"id\":\"x\"}}]},"
                + "{\"type\":\"rule\"}");

        assertThat(AdfText.of(doc)).isEqualTo("Host\ngw-1");
    }

    @Test
    void givesNullForAnEmptyOrMissingDocumentAndKeepsPlainText_AC_001_2() throws Exception {
        assertThat(AdfText.of(null)).isNull();
        assertThat(AdfText.of(NullNode.getInstance())).isNull();
        assertThat(AdfText.of(adf(""))).isNull();
        assertThat(AdfText.of(JSON.readTree("\"plain text\""))).isEqualTo("plain text");
    }
}
