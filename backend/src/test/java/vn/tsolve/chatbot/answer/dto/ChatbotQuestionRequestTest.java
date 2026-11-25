package vn.tsolve.chatbot.answer.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import vn.tsolve.chatbot.exception.InvalidChatbotRequestException;

/** The {@code askForProject} request: a bad history is refused before the service, so no AI call and no row. */
class ChatbotQuestionRequestTest {
    private static final ChatbotTurnRequest TURN = new ChatbotTurnRequest("old question", "old answer");

    @ParameterizedTest
    @MethodSource("badHistories")
    void rejectsBadHistory_AC_007_3(List<ChatbotTurnRequest> history) {
        assertThatThrownBy(() -> new ChatbotQuestionRequest("question", history))
                .isInstanceOfSatisfying(InvalidChatbotRequestException.class, refused -> {
                    assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(refused.getCode()).isEqualTo("MSG06");
                    assertThat(refused.getField()).isNull();
                });
    }

    @Test
    void acceptsHistoryWithinLimits_AC_007_3() {
        var atLimits = new ChatbotTurnRequest("q".repeat(1000), "a".repeat(10000));
        var empty = new ChatbotTurnRequest("", "");

        assertThat(new ChatbotQuestionRequest("question", Collections.nCopies(10, atLimits)).history())
                .hasSize(10);
        assertThat(new ChatbotQuestionRequest("question", Collections.nCopies(10, empty)).history())
                .hasSize(10);
        assertThat(new ChatbotQuestionRequest("question", List.of()).history()).isEmpty();
    }

    private static Stream<List<ChatbotTurnRequest>> badHistories() {
        List<ChatbotTurnRequest> nullTurn = new ArrayList<>();
        nullTurn.add(null);
        return Stream.of(
                null,
                Collections.nCopies(11, TURN),
                nullTurn,
                List.of(new ChatbotTurnRequest(null, "answer")),
                List.of(new ChatbotTurnRequest("old question", null)),
                List.of(new ChatbotTurnRequest("q".repeat(1001), "answer")),
                List.of(new ChatbotTurnRequest("old question", "a".repeat(10001))));
    }
}
