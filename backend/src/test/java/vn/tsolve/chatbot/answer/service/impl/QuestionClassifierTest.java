package vn.tsolve.chatbot.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.chatbot.answer.service.impl.QuestionClassifier.Category;

class QuestionClassifierTest {
    private final QuestionClassifier classifier = new QuestionClassifier(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(
            strings = {
                "hi",
                "hello",
                "hey",
                "thanks",
                "thank you",
                "thanks a lot",
                "thx",
                "ok thanks",
                "good morning",
                "good afternoon",
                "xin chào",
                "chào",
                "chào bạn",
                "cảm ơn",
                "cám ơn",
                "cảm ơn bạn",
                "cảm ơn nhiều"
            })
    void matchesGreetingPhrases_AC_009_1(String phrase) {
        assertThat(QuestionClassifier.isGreeting(phrase)).isTrue();
        assertThat(QuestionClassifier.isGreeting(phrase + ", how do I reset my VPN?"))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "  HELLO!!! 👋🏽 ",
                "Cảm ơn!! 🙏",
                "  GOOD\tMORNING. ",
                "thank-you",
                "ca\u0309m o\u031bn",
                "\u00a0CHÀO\u2003BẠN\u00a0"
            })
    void normalizesPunctuationEmojiAndCase_AC_009_1(String phrase) {
        assertThat(QuestionClassifier.isGreeting(phrase)).isTrue();
        assertThat(QuestionClassifier.isGreeting("xin chao")).isFalse();
        assertThat(QuestionClassifier.isGreeting("hi, how do I reset my VPN?")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"thanks \u2764\ufe0f", "\ud83d\udc68\u200d\ud83d\udcbb thanks", "thanks\u200b"})
    void stripsEmojiJoinersAndSelectors_AC_009_1_BUG_326(String phrase) {
        assertThat(QuestionClassifier.isGreeting(phrase)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GREETING", "OFF_TOPIC", "INJECTION", "QUESTION"})
    void parsesCategoryAndQuery_AC_009_2(String category) {
        String query = category.equals("QUESTION") ? "VPN Windows " + "q".repeat(1200) : "";
        var result = classifier.parse("{\"category\":\"  " + category.toLowerCase(java.util.Locale.ROOT)
                + "  \",\"query\":\"  " + query + "  \"}");
        assertThat(result.category()).isEqualTo(Category.valueOf(category));
        assertThat(result.query()).isEqualTo(query);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "prose",
                "{",
                "[]",
                "null",
                "42",
                "{}",
                "{\"category\":null,\"query\":\"VPN\"}",
                "{\"category\":123,\"query\":\"VPN\"}",
                "{\"category\":\"OFF_TOPIC\",\"query\":\"\"} trailing"
            })
    void failsOpenOnBadJson_AC_009_6(String json) {
        assertFallback(classifier.parse(json));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"category\":\"OTHER\",\"query\":\"VPN\"}",
                "{\"category\":\"QUESTION\",\"query\":\"  \"}",
                "{\"category\":\"QUESTION\"}",
                "{\"category\":\"QUESTION\",\"query\":null}",
                "{\"category\":\"QUESTION\",\"query\":12}",
                "{\"category\":\"QUESTION\",\"query\":{}}"
            })
    void failsOpenOnUnknownCategoryOrBlankQuery_AC_009_6(String json) {
        assertFallback(classifier.parse(json));
    }

    private static void assertFallback(QuestionClassifier.Classification result) {
        assertThat(result).isNotNull();
        assertThat(result.category()).isEqualTo(Category.QUESTION);
        assertThat(result.query()).isEmpty();
    }
}
