package vn.tsolve.worker.client;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.util.EmailAddressRules;

@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailSender {
    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, @Value("${TSOLVE_SMTP_FROM}") String from) {
        this.mail = mail;
        this.from = EmailAddressRules.singleMailbox(from);
    }

    public void send(OutboundEmail outbound) {
        OutboundEmail email = EmailAddressRules.validate(outbound);
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, email.bodyHtml() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            if (email.bodyHtml() == null) {
                helper.setText(email.bodyText());
            } else {
                helper.setText(email.bodyText(), email.bodyHtml());
            }
            mail.send(message);
        } catch (MailException | MessagingException ignored) {
            // Neither the cause nor transport diagnostics are safe to expose.
            throw new DeliveryException();
        }
    }

    public static final class DeliveryException extends RuntimeException {
        public DeliveryException() {
            super("Email delivery failed");
        }
    }
}
