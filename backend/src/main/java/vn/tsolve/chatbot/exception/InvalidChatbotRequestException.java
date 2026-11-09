package vn.tsolve.chatbot.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class InvalidChatbotRequestException extends MessageCodeException {
    public InvalidChatbotRequestException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
    }
}
