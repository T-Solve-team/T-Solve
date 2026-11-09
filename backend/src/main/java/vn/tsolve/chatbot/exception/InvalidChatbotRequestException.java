package vn.tsolve.chatbot.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** 400 MSG06 with no field error: a chatbot request the web app never sends (bad ids or history). */
public class InvalidChatbotRequestException extends MessageCodeException {
    public InvalidChatbotRequestException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
    }
}
