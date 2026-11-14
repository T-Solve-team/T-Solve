package vn.tsolve.worker.client;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class ReviewNotificationSender {
    private final JavaMailSender mail;
    private final String from;
    private final ReviewEmailRenderer renderer = new ReviewEmailRenderer();

    public ReviewNotificationSender(JavaMailSender mail, @Value("${TSOLVE_SMTP_FROM}") String from) {
        this.mail = mail;
        this.from = singleMailbox(from);
    }

    public void send(String recipient, ReviewEmail email) {
        String mailbox = singleMailbox(recipient);
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from, "T-Solve"));
            helper.setTo(mailbox);
            helper.setSubject(("[" + email.ticketKey() + "] " + email.decision().subject)
                    .replace('\r', ' ')
                    .replace('\n', ' '));
            helper.setText(renderer.plain(email), renderer.html(email));
            mail.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException ignored) {
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
        REJECT("Your ticket was rejected"),
        REQUEST_CHANGES("Changes requested for your ticket");

        private final String subject;

        Decision(String subject) {
            this.subject = subject;
        }
    }

    public static final class DeliveryException extends RuntimeException {
        private DeliveryException() {
            super("Review email delivery failed");
        }
    }
}
