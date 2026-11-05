package vn.tsolve.ai.client;

/** One turn of a chat completion. */
public record ChatMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
