package vn.tsolve.chatbot.rating.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class InvalidChatbotRatingException extends MessageCodeException {
    public InvalidChatbotRatingException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
    }
}
