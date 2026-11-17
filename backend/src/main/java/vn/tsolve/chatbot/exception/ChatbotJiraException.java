package vn.tsolve.chatbot.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class ChatbotJiraException extends MessageCodeException {
    public ChatbotJiraException(HttpStatus status, String code) {
        super(status, code, Map.of());
    }
}
