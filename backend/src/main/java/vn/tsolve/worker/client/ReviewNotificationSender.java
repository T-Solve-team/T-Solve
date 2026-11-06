package vn.tsolve.worker.client;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class ReviewNotificationSender {
    private final JavaMailSender mail;
    private final String from;

    public ReviewNotificationSender(JavaMailSender mail, @Value("${TSOLVE_SMTP_FROM}") String from) {
        this.mail = mail;
        this.from = singleMailbox(from);
    }

    public void send(Decision decision, String recipient, String ticketKey, String feedback) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(singleMailbox(recipient));
        message.setSubject(decision.subject);
        message.setText("Ticket %s\n\n%s\n%s".formatted(ticketKey, decision.feedbackHeading, feedback));
        try {
            mail.send(message);
        } catch (MailException ignored) {
            // The transport exception can carry recipient addresses and message details.
            throw new DeliveryException();
        }
    }

    private static String singleMailbox(String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A single mailbox is required");
        }
        try {
            var addresses = InternetAddress.parse(value, true);
            if (addresses.length == 1 && !addresses[0].isGroup()) {
                addresses[0].validate();
                return addresses[0].getAddress();
            }
        } catch (AddressException ignored) {
            // Report the same validation error without echoing the supplied address.
        }
        throw new IllegalArgumentException("A single mailbox is required");
    }

    public enum Decision {
        REJECT("Ticket rejected", "Rejection reason"),
        REQUEST_CHANGES("Changes requested for your ticket", "Review comment");

        private final String subject;
        private final String feedbackHeading;

        Decision(String subject, String feedbackHeading) {
            this.subject = subject;
            this.feedbackHeading = feedbackHeading;
        }
    }

    public static final class DeliveryException extends RuntimeException {
        private DeliveryException() {
            super("Review email delivery failed");
        }
    }
}
