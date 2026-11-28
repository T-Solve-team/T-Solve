package vn.tsolve.worker.client;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.util.EmailAddressRules;

@Component
@Profile("worker")
@Conditional(SmtpEmailSender.ConfiguredHostCondition.class)
public class SmtpEmailSender {
    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, @Value("${TSOLVE_SMTP_FROM}") String from) {
        this.mail = mail;
        this.from = EmailAddressRules.singleMailbox(from);
    }

    public void send(OutboundEmail outbound) {
        // Queue validation stays strict; direct delivery normalizes subject header line breaks.
        if (outbound != null && outbound.subject() != null) {
            outbound = new OutboundEmail(
                    outbound.recipient(),
                    outbound.subject().replace('\r', ' ').replace('\n', ' '),
                    outbound.bodyText(),
                    outbound.bodyHtml());
        }
        OutboundEmail email = EmailAddressRules.validate(outbound);
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, email.bodyHtml() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, "T-Solve"));
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            if (email.bodyHtml() == null) {
                helper.setText(email.bodyText());
            } else {
                helper.setText(email.bodyText(), email.bodyHtml());
            }
            mail.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException failure) {
            throw new DeliveryException(failure);
        }
    }

    public static final class DeliveryException extends RuntimeException {
        public DeliveryException() {
            super("Email delivery failed");
        }

        public DeliveryException(Throwable cause) {
            super("Email delivery failed", cause);
        }
    }

    static final class ConfiguredHostCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // ConditionalOnProperty also matches the empty default of the .env placeholder.
            return StringUtils.hasText(context.getEnvironment().getProperty("spring.mail.host"));
        }
    }
}
