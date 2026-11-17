package vn.tsolve.tracker.dto;

/** Whether the Chatbot can create Task issues in a Jira project as the service account (workspace AC-012.7). */
public enum ChatbotProjectCheck {
    READY,
    /** No non-sub-task issue type Task, or a field the Chatbot does not send is required. */
    NOT_READY,
    /** No service account, a permission missing, or Jira refused (any 4xx). */
    DENIED,
    /** Jira answered 429 or 5xx, timed out or could not be reached. */
    UNAVAILABLE
}
