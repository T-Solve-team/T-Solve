package vn.tsolve.jiraapp.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The Draft reminder over the worker's SMTP settings (`spring.mail.*`, `TSOLVE_SMTP_FROM`), the same settings as
 * `ticket-review`'s notifications but its own sender, so that class stays untouched.
 */
@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpDraftReminderMailClient implements DraftReminderMailClient {

    private final JavaMailSender mail;
    private final String from;

    public SmtpDraftReminderMailClient(JavaMailSender mail, @Value("${TSOLVE_SMTP_FROM}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
