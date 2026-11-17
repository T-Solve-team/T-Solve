package vn.tsolve.chatbot.answer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class QuestionClassifier {
    private static final Set<String> GREETINGS = Set.of(
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
            "cảm ơn nhiều");
    // \p{Cf} and the variation selectors catch the ZWJ, zero-width space and U+FE0F parts of emoji.
    private static final Pattern PUNCTUATION_AND_EMOJI =
            Pattern.compile("[\\p{P}\\p{So}\\p{Sk}\\p{Cf}\\x{FE00}-\\x{FE0F}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Classification FALLBACK = new Classification(Category.QUESTION, "");
    private final ObjectMapper json;

    QuestionClassifier(ObjectMapper json) {
        this.json = json;
    }

    static boolean isGreeting(String question) {
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        normalized = PUNCTUATION_AND_EMOJI.matcher(normalized).replaceAll(" ");
        return GREETINGS.contains(WHITESPACE.matcher(normalized).replaceAll(" ").trim());
    }

    Classification parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return FALLBACK;
        }
        try {
            JsonNode data = json.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(reply);
            if (data == null || !data.path("category").isTextual()) {
                return FALLBACK;
            }
            Category category =
                    Category.valueOf(data.get("category").textValue().trim().toUpperCase(Locale.ROOT));
            if (category != Category.QUESTION) {
                return new Classification(category, "");
            }
            JsonNode query = data.path("query");
            if (!query.isTextual() || query.textValue().isBlank()) {
                return FALLBACK;
            }
            return new Classification(category, query.textValue().trim());
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            // A malformed classifier reply must not prevent a search or expose the reply in a log.
            return FALLBACK;
        }
    }

    enum Category {
        GREETING,
        OFF_TOPIC,
        INJECTION,
        QUESTION
    }

    record Classification(Category category, String query) {}
}
