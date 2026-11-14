package vn.tsolve.jiraapp.client;

/** Sends the Draft reminder email (jira-app STORY-011). Present only where the worker has an SMTP sender. */
public interface DraftReminderMailClient {

    /** Sends one plain-text email; throws when it cannot be delivered, so the job is retried (AC-011.4). */
    void send(String recipient, String subject, String body);
}
