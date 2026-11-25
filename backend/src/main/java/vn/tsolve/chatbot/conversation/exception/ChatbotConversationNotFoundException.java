package vn.tsolve.chatbot.conversation.exception;

import java.util.Map;
import vn.tsolve.common.exception.NotFoundException;

/** An unknown conversation id: not a UUID, no such conversation, or one of another user (AC-013.4). */
public class ChatbotConversationNotFoundException extends NotFoundException {
    public ChatbotConversationNotFoundException() {
        super("MSG04", Map.of());
    }
}
