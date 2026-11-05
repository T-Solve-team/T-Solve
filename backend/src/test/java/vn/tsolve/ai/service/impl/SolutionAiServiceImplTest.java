package vn.tsolve.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.dto.SolutionSections;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.exception.AiUnavailableException;

class SolutionAiServiceImplTest {

    private static final String VALID_ANSWER =
            """
            {"symptoms":"  Failure  ","rootCause":"  Cause  ","resolutionSteps":"  Restart  ",
             "workaround":"  Wait  ","scopeOfApplicability":"  Server  "}
            """;

    @Test
    @DisplayName("AC-004.1 asks for five JSON sections and returns a trimmed AI draft")
    void draftsFiveSections() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);

        SolutionDraft draft =
                service(client).draft(new IssueContent("Outage", "Service stopped", List.of("Restarted")));

        assertThat(draft.isAiDraft()).isTrue();
        assertThat(draft.sections()).isEqualTo(new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server"));
        assertThat(client.messages).hasSize(2);
        assertThat(client.messages.get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(client.messages.get(0).content()).contains("Vietnamese", "English", "plain text");
        assertThat(client.messages.get(1).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(client.messages.get(1).content()).contains("Outage", "Service stopped", "Restarted");
        assertThat(client.schema).isNotNull();
        assertThat(client.schema.get("type")).isEqualTo("object");
        assertThat(((Map<?, ?>) client.schema.get("properties"))
                        .keySet().stream().map(Object::toString).toList())
                .contains("symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability");
    }

    @Test
    @DisplayName("AC-004.2 falls back when a required section is blank")
    void blankRequiredSectionFallsBack() {
        for (String field : List.of("symptoms", "rootCause", "resolutionSteps", "scopeOfApplicability")) {
            String answer = VALID_ANSWER.replaceFirst("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"   \"");
            SolutionDraft draft = service(new StubLlmClient(answer))
                    .draft(new IssueContent("Summary", " Description ", List.of("First", " Last ")));

            assertThat(draft.isAiDraft()).as(field).isFalse();
            assertThat(draft.sections()).as(field).isEqualTo(new SolutionSections("Description", "", "Last", "", ""));
        }
    }

    @Test
    @DisplayName("AC-004.2 falls back when trimmed sections total 10001 characters")
    void oversizedAnswerFallsBack() {
        String answer =
                """
                {"symptoms":"%s","rootCause":"b","resolutionSteps":"c","workaround":"d","scopeOfApplicability":"e"}
                """
                        .formatted("a".repeat(9_997));

        SolutionDraft draft =
                service(new StubLlmClient(answer)).draft(new IssueContent("Summary", "Description", List.of("Last")));

        assertThat(draft).isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
    }

    @Test
    @DisplayName("AC-004.2 keeps a draft when trimmed sections total exactly 10000 characters")
    void exactOutputLimitIsAccepted() {
        String answer =
                """
                {"symptoms":"%s","rootCause":"b","resolutionSteps":"c","workaround":"d","scopeOfApplicability":"e"}
                """
                        .formatted("a".repeat(9_996));

        SolutionDraft draft =
                service(new StubLlmClient(answer)).draft(new IssueContent("Summary", "Description", List.of()));

        assertThat(draft.isAiDraft()).isTrue();
        assertThat(draft.sections().symptoms()).hasSize(9_996);
    }

    @Test
    @DisplayName("AC-004.2 permits a blank workaround when the other sections are filled")
    void blankWorkaroundKeepsAiDraft() {
        String answer = VALID_ANSWER.replace("\"  Wait  \"", "\"   \"");

        SolutionDraft draft =
                service(new StubLlmClient(answer)).draft(new IssueContent("Summary", "Description", List.of()));

        assertThat(draft.isAiDraft()).isTrue();
        assertThat(draft.sections()).isEqualTo(new SolutionSections("Failure", "Cause", "Restart", "", "Server"));
    }

    @Test
    @DisplayName("AC-004.2 falls back on invalid JSON, wrong field type, and missing field")
    void malformedAnswersFallBack() {
        for (String answer : List.of(
                "{broken",
                VALID_ANSWER.replace("\"  Failure  \"", "42"),
                "{\"symptoms\":\"Failure\",\"rootCause\":\"Cause\",\"resolutionSteps\":\"Restart\",\"workaround\":\"Wait\"}")) {
            SolutionDraft draft = service(new StubLlmClient(answer))
                    .draft(new IssueContent("Summary", "Description", List.of("Last")));

            assertThat(draft)
                    .as(answer)
                    .isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
        }
    }

    @ParameterizedTest
    @MethodSource("malformedBoundaryAnswers")
    @DisplayName("AC-004.2/004.3 falls back for trailing tokens, null fields, non-object and blank answers")
    void malformedBoundaryAnswersFallBack(String answer) {
        SolutionDraft draft =
                service(new StubLlmClient(answer)).draft(new IssueContent("Summary", "Description", List.of("Last")));

        assertThat(draft).isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
    }

    private static Stream<String> malformedBoundaryAnswers() {
        return Stream.of(VALID_ANSWER + " junk", VALID_ANSWER.replace("\"  Failure  \"", "null"), "[]", "", "   ");
    }

    @Test
    @DisplayName("AC-004.3 an AI exception never escapes draft")
    void aiFailureDoesNotEscape() {
        StubLlmClient client = new StubLlmClient(null);
        IssueContent issue = new IssueContent("Summary", "Description", List.of("Last"));

        assertThatCode(() -> service(client).draft(issue)).doesNotThrowAnyException();
        assertThat(service(client).draft(issue))
                .isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
    }

    @Test
    @DisplayName("AC-004.3 an unexpected LLM runtime failure returns the fallback")
    void unexpectedRuntimeFailureFallsBack() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER, new IllegalStateException("LLM failure"));

        SolutionDraft draft = service(client).draft(new IssueContent("Summary", "Description", List.of("Last")));

        assertThat(draft).isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
    }

    @Test
    @DisplayName("AC-004.4 cuts a 50000-character description at 12000 without counting the summary")
    void cutsLongDescription() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);
        String expectedDescription = "x".repeat(11_999) + "Y";
        String description = expectedDescription + "Z" + "x".repeat(37_999);

        String summary = "S".repeat(12_000);
        service(client).draft(new IssueContent(summary, description, List.of("LATEST")));

        String userMessage = client.messages.get(1).content();
        String sentDescription = userMessage.substring(
                userMessage.indexOf("Description: ") + "Description: ".length(),
                userMessage.indexOf("\nComments (oldest first):"));
        assertThat(userMessage).contains(summary).doesNotContain("Z", "LATEST");
        assertThat(sentDescription)
                .hasSize(12_000)
                .isEqualTo(expectedDescription)
                .endsWith("Y");
    }

    @Test
    @DisplayName(
            "AC-004.4 takes newest fitting comments, stops at first that does not fit, and sends them oldest first")
    void selectsNewestFittingComments() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);

        service(client)
                .draft(new IssueContent("SUMMARY", "d".repeat(11_988), List.of("OLD", "TOO-LONG-12", "NEWER", "LAST")));

        String userMessage = client.messages.get(1).content();
        assertThat(userMessage).contains("SUMMARY", "NEWER", "LAST");
        assertThat(userMessage).doesNotContain("OLD", "TOO-LONG-12");
        assertThat(userMessage.indexOf("NEWER")).isLessThan(userMessage.indexOf("LAST"));
    }

    @Test
    @DisplayName("AC-004.4 includes a comment that exactly fits the remaining input room")
    void exactFitCommentIsSent() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);

        service(client).draft(new IssueContent("Summary", "d".repeat(11_994), List.of("EXACT!")));

        assertThat(client.messages.get(1).content()).contains("Comments (oldest first):\nEXACT!\n");
    }

    @Test
    @DisplayName("AC-004.4 sends no comments when the newest comment exceeds the remaining room")
    void oversizedNewestCommentStopsSelection() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);

        service(client).draft(new IssueContent("Summary", "d".repeat(11_996), List.of("AB", "OVERSIZED")));

        String userMessage = client.messages.get(1).content();
        assertThat(userMessage.substring(
                        userMessage.indexOf("Comments (oldest first):") + "Comments (oldest first):".length()))
                .isEqualTo("\n\n");
    }

    @Test
    @DisplayName("AC-004.5 null or blank description and absent comments yield empty fallback fields")
    void absentIssueContentFallsBackSafely() {
        for (String description : new String[] {null, "  "}) {
            for (boolean nullComments : List.of(true, false)) {
                List<String> comments = nullComments ? null : List.of();
                SolutionDraft draft =
                        service(new StubLlmClient(null)).draft(new IssueContent("Summary", description, comments));

                assertThat(draft).isEqualTo(new SolutionDraft(new SolutionSections("", "", "", "", ""), false));
            }
        }
    }

    @Test
    @DisplayName("AC-004.5 preserves each present fallback field and accepts a null comment element")
    void oneSidedIssueContentFallsBackSafely() {
        assertThat(service(new StubLlmClient(null)).draft(new IssueContent("Summary", " Description ", List.of())))
                .isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "", "", ""), false));
        assertThat(service(new StubLlmClient(null)).draft(new IssueContent("Summary", null, List.of(" Last "))))
                .isEqualTo(new SolutionDraft(new SolutionSections("", "", "Last", "", ""), false));
        assertThat(service(new StubLlmClient(null))
                        .draft(new IssueContent("Summary", "Description", Arrays.asList(null, "Last"))))
                .isEqualTo(new SolutionDraft(new SolutionSections("Description", "", "Last", "", ""), false));
    }

    @Test
    @DisplayName("AC-004.5 strips ideographic whitespace from fallback symptoms")
    void ideographicSpaceIsBlankFallbackDescription() {
        SolutionDraft draft = service(new StubLlmClient(null)).draft(new IssueContent("Summary", "\u3000", List.of()));

        assertThat(draft).isEqualTo(new SolutionDraft(new SolutionSections("", "", "", "", ""), false));
    }

    @Test
    @DisplayName("AC-004.1 preserves Vietnamese text with diacritics through JSON parsing")
    void preservesVietnameseText() {
        String answer =
                """
                {"symptoms":"Lỗi đăng nhập","rootCause":"Sai cấu hình","resolutionSteps":"Khởi động lại",
                 "workaround":"Tạm chờ","scopeOfApplicability":"Máy chủ Hà Nội"}
                """;

        SolutionDraft draft = service(new StubLlmClient(answer))
                .draft(new IssueContent("Không đăng nhập được", "Màn hình báo lỗi", List.of()));

        assertThat(draft.sections())
                .isEqualTo(new SolutionSections(
                        "Lỗi đăng nhập", "Sai cấu hình", "Khởi động lại", "Tạm chờ", "Máy chủ Hà Nội"));
    }

    @Test
    @DisplayName("AC-005.1 sends filled sections with the five-field schema and returns stripped rewrite")
    void rewritesFilledSections() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);
        SolutionSections given =
                new SolutionSections(" Login fails ", " Bad setting ", " Restart ", " Wait ", " Server ");

        SolutionSections rewritten = service(client).rewrite(given);

        assertThat(rewritten).isEqualTo(new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server"));
        assertThat(client.messages).hasSize(2);
        assertThat(client.messages.get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(client.messages.get(0).content())
                .contains("meaning", "five-section", "Vietnamese", "English", "plain text");
        assertThat(client.messages.get(1).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(client.messages.get(1).content())
                .contains("Login fails", "Bad setting", "Restart", "Wait", "Server");
        assertThat(client.schema).isNotNull();
        assertThat(client.schema.get("type")).isEqualTo("object");
        assertThat(((Map<?, ?>) client.schema.get("properties"))
                        .keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder(
                        "symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability");
    }

    @Test
    @DisplayName("AC-005.2 forces model-filled empty sections back to empty in a partial Solution")
    void keepsUnfilledSectionsEmpty() {
        StubLlmClient client = new StubLlmClient(VALID_ANSWER);
        SolutionSections given = new SolutionSections("Failure", "  ", "Restart", null, "\u3000");

        SolutionSections rewritten = service(client).rewrite(given);

        assertThat(rewritten).isEqualTo(new SolutionSections("Failure", "", "Restart", "", ""));
        assertThat(client.messages.get(1).content())
                .contains("Symptoms: Failure", "Resolution steps: Restart")
                .doesNotContain("Root cause", "Workaround", "Scope", "Cause", "Wait", "Server");
    }

    @ParameterizedTest
    @MethodSource("malformedEmptySectionAnswers")
    @DisplayName("AC-005.2 ignores missing, null and non-text answers for empty input sections")
    void ignoresMalformedEmptySectionAnswers(String answer) {
        SolutionSections given = new SolutionSections("Failure", "", "", "", "");

        assertThat(service(new StubLlmClient(answer)).rewrite(given))
                .isEqualTo(new SolutionSections("Failure", "", "", "", ""));
    }

    private static Stream<String> malformedEmptySectionAnswers() {
        return Stream.of(
                "{\"symptoms\":\"Failure\"}",
                "{\"symptoms\":\"Failure\",\"rootCause\":null,\"resolutionSteps\":null,\"workaround\":null,\"scopeOfApplicability\":null}",
                "{\"symptoms\":\"Failure\",\"rootCause\":42,\"resolutionSteps\":false,\"workaround\":[],\"scopeOfApplicability\":{}}");
    }

    @Test
    @DisplayName("AC-005.3 maps AI and unexpected runtime failures to rewrite unavailable")
    void callFailuresBecomeRewriteUnavailable() {
        SolutionSections given = new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server");
        for (RuntimeException failure : List.of(new AiUnavailableException(), new IllegalStateException("failure"))) {
            assertThatThrownBy(() ->
                            service(new StubLlmClient(VALID_ANSWER, failure)).rewrite(given))
                    .isInstanceOf(AiRewriteUnavailableException.class);
        }
    }

    @Test
    @DisplayName("AC-005.3 rejects a model answer that empties any filled section")
    void rejectsEmptiedFilledSection() {
        SolutionSections given = new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server");
        for (String field : List.of("symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability")) {
            String answer = VALID_ANSWER.replaceFirst("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"   \"");
            assertThatThrownBy(() -> service(new StubLlmClient(answer)).rewrite(given))
                    .as(field)
                    .isInstanceOf(AiRewriteUnavailableException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("malformedRewriteAnswers")
    @DisplayName("AC-005.3 rejects blank, invalid, trailing, non-object, and malformed-field answers")
    void rejectsMalformedRewriteAnswer(String answer) {
        SolutionSections given = new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server");

        assertThatThrownBy(() ->
                        new SolutionAiServiceImpl((messages, schema) -> answer, new ObjectMapper()).rewrite(given))
                .isInstanceOf(AiRewriteUnavailableException.class)
                .extracting(failure -> ((AiRewriteUnavailableException) failure).getCode())
                .isEqualTo("MSG68");
    }

    private static Stream<String> malformedRewriteAnswers() {
        return Stream.of(
                null,
                "null",
                "",
                "   ",
                "{broken",
                VALID_ANSWER + " junk",
                "[]",
                VALID_ANSWER.replace("\"  Failure  \"", "null"),
                VALID_ANSWER.replace("\"  Failure  \"", "42"),
                VALID_ANSWER.replaceFirst(",\"scopeOfApplicability\":\"[^\"]*\"", ""));
    }

    @Test
    @DisplayName("AC-005.3 accepts 10000 stripped characters and rejects 10001")
    void rewriteOutputLimit() {
        SolutionSections given = new SolutionSections("a", "b", "c", "d", "e");
        String exact =
                "{\"symptoms\":\"%s\",\"rootCause\":\"b\",\"resolutionSteps\":\"c\",\"workaround\":\"d\",\"scopeOfApplicability\":\"e\"}"
                        .formatted("a".repeat(9_996));
        String over = exact.replace("a".repeat(9_996), "a".repeat(9_997));

        assertThat(service(new StubLlmClient(exact)).rewrite(given).symptoms()).hasSize(9_996);
        String padded = exact.replace("a".repeat(9_996), "  " + "a".repeat(9_996) + "  ");
        assertThat(service(new StubLlmClient(padded)).rewrite(given).symptoms()).isEqualTo("a".repeat(9_996));

        SolutionSections partial = new SolutionSections("a", "", "", "", "");
        String fillsEmpty = exact.replace("\"b\"", "\"" + "b".repeat(10_001) + "\"");
        assertThat(service(new StubLlmClient(fillsEmpty)).rewrite(partial))
                .isEqualTo(new SolutionSections("a".repeat(9_996), "", "", "", ""));
        assertThatThrownBy(() -> service(new StubLlmClient(over)).rewrite(given))
                .isInstanceOf(AiRewriteUnavailableException.class);
    }

    @Test
    @DisplayName("AC-005.4 returns all blank or null sections unchanged without an LLM call")
    void allEmptySectionsSkipLlm() {
        for (SolutionSections given : List.of(
                new SolutionSections("", "", "", "", ""), new SolutionSections(null, " ", "\u3000", null, "\n"))) {
            StubLlmClient client = new StubLlmClient(VALID_ANSWER);

            assertThat(service(client).rewrite(given)).isSameAs(given);
            assertThat(client.messages).isNull();
        }
    }

    @Test
    @DisplayName("AC-005.5 returns a new value without changing the supplied Solution or using storage")
    void rewriteOnlyReturnsValue() {
        SolutionSections given = new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server");

        SolutionSections rewritten = service(new StubLlmClient(VALID_ANSWER)).rewrite(given);

        assertThat(rewritten).isNotSameAs(given);
        assertThat(given).isEqualTo(new SolutionSections("Failure", "Cause", "Restart", "Wait", "Server"));
        assertThat(Arrays.stream(SolutionAiServiceImpl.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .map(field -> field.getType().getName())
                        .toList())
                .containsExactlyInAnyOrder(LlmClient.class.getName(), ObjectMapper.class.getName());
    }

    @Test
    @DisplayName("AC-005.1 preserves Vietnamese diacritics through rewrite JSON")
    void rewritePreservesVietnamese() {
        String answer =
                "{\"symptoms\":\"Lỗi đăng nhập\",\"rootCause\":\"Sai cấu hình\",\"resolutionSteps\":\"Khởi động lại\",\"workaround\":\"Tạm chờ\",\"scopeOfApplicability\":\"Máy chủ Hà Nội\"}";
        StubLlmClient client = new StubLlmClient(answer);

        SolutionSections rewritten = service(client)
                .rewrite(new SolutionSections(
                        "Lỗi đăng nhập", "Sai cấu hình", "Khởi động lại", "Tạm chờ", "Máy chủ Hà Nội"));

        assertThat(rewritten)
                .isEqualTo(new SolutionSections(
                        "Lỗi đăng nhập", "Sai cấu hình", "Khởi động lại", "Tạm chờ", "Máy chủ Hà Nội"));
        assertThat(client.messages.get(1).content()).contains("Lỗi đăng nhập", "Máy chủ Hà Nội");
    }

    private SolutionAiServiceImpl service(StubLlmClient client) {
        return new SolutionAiServiceImpl(client, new ObjectMapper());
    }

    private static final class StubLlmClient implements LlmClient {
        private final String answer;
        private final RuntimeException failure;
        private List<ChatMessage> messages;
        private Map<String, Object> schema;

        private StubLlmClient(String answer) {
            this(answer, null);
        }

        private StubLlmClient(String answer, RuntimeException failure) {
            this.answer = answer;
            this.failure = failure;
        }

        @Override
        public String chat(List<ChatMessage> messages, Map<String, Object> schema) {
            this.messages = messages;
            this.schema = schema;
            if (failure != null) {
                throw failure;
            }
            if (answer == null) {
                throw new AiUnavailableException();
            }
            return answer;
        }
    }
}
