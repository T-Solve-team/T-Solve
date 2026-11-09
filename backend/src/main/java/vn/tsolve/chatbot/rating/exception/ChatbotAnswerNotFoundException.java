package vn.tsolve.chatbot.rating.exception;

import java.util.Map;
import vn.tsolve.common.exception.NotFoundException;

public class ChatbotAnswerNotFoundException extends NotFoundException {
    public ChatbotAnswerNotFoundException() {
        super("MSG04", Map.of());
    }
}
