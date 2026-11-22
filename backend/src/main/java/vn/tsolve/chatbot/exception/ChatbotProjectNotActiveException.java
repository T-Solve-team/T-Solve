package vn.tsolve.chatbot.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** A question asked for a Project that is unknown or not Active (403 MSG08, AC-012.4). */
public class ChatbotProjectNotActiveException extends MessageCodeException {
    public ChatbotProjectNotActiveException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
