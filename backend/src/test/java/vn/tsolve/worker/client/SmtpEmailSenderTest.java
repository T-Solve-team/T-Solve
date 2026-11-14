package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;

@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailSenderTest {
    private static final String WRITER = "writer@example.test";
    private static final String FROM = "review@example.test";
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(mail, FROM);
    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(SmtpEmailSender.class);

    @Test
    @DisplayName("AC-001.1 sends the supplied plain text with Unicode unchanged")
    void sendsPlainText() throws Exception {
        var message = message();
        sender.send(new OutboundEmail("Writer <" + WRITER + ">", "Thông báo", "Thiếu bằng chứng", null));
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Thông báo");
        assertThat(message.getContent()).isEqualTo("Thiếu bằng chứng");
        assertThat(message.isMimeType("text/plain")).isTrue();
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(WRITER);
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("T-Solve");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo(FROM);
        verify(mail).send(message);
    }

    @Test
    @DisplayName("AC-001.2 preserves both supplied Unicode alternatives")
    void sendsMultipartAlternatives() throws Exception {
        var message = message();
        sender.send(new OutboundEmail(WRITER, "Thông báo", "Thiếu bằng chứng", "<p>Thiếu bằng chứng</p>"));
        message.saveChanges();
        var mixed = (jakarta.mail.Multipart) message.getContent();
        var related = (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
        var alternative = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
        assertThat(alternative.getContentType()).startsWith("multipart/alternative");
        assertThat(alternative.getCount()).isEqualTo(2);
        assertThat(alternative.getBodyPart(0).getContent()).isEqualTo("Thiếu bằng chứng");
        assertThat(alternative.getBodyPart(1).getContent()).isEqualTo("<p>Thiếu bằng chứng</p>");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("T-Solve");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo(FROM);
        verify(mail).send(message);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "a@example.test,b@example.test",
                "team:a@example.test;",
                "a@example.test\r\nBcc: b@example.test"
            })
    @DisplayName("AC-001.3 rejects anything other than one mailbox before delivery")
    void rejectsInvalidRecipient(String recipient) {
        assertThatThrownBy(() -> sender.send(new OutboundEmail(recipient, "Subject", "Text", null)))
                .isInstanceOf(InvalidOutboundEmailException.class)
                .hasMessage("Invalid outbound email")
                .hasNoCause();
        verifyNoInteractions(mail);
    }

    @Test
    @DisplayName("AC-001.4 AC-001.6 one safe failure without logging mail data")
    void reportsSafeFailure(CapturedOutput output) {
        message();
        doThrow(new MailSendException(WRITER + " private-feedback credential"))
                .when(mail)
                .send(any(MimeMessage.class));
        assertThatThrownBy(() -> sender.send(new OutboundEmail(WRITER, "Private subject", "private-feedback", null)))
                .isInstanceOf(SmtpEmailSender.DeliveryException.class)
                .hasMessage("Email delivery failed")
                .hasNoCause();
        verify(mail).send(any(MimeMessage.class));
        assertThat(output.getAll()).doesNotContain(WRITER, "Private subject", "private-feedback", "credential");
    }

    @Test
    @DisplayName("AC-001.5 configures finite mail timeouts")
    void configuresTimeouts() {
        context.withPropertyValues(
                        "spring.profiles.active=worker", "spring.mail.host=localhost", "TSOLVE_SMTP_FROM=" + FROM)
                .run(web -> {
                    assertThat(web).hasSingleBean(SmtpEmailSender.class);
                    assertThat(web.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "5000")
                            .containsEntry("mail.smtp.timeout", "3000")
                            .containsEntry("mail.smtp.writetimeout", "5000");
                });
    }

    @Test
    @DisplayName("AC-001.7 web context starts without SMTP settings")
    void startsWithoutSmtp() {
        context.run(web -> assertThat(web)
                .hasNotFailed()
                .doesNotHaveBean(JavaMailSender.class)
                .doesNotHaveBean(SmtpEmailSender.class));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = "<p>HTML</p>")
    @DisplayName("AC-001.1 AC-001.2 replaces every subject line break for plain text and HTML")
    void replacesSubjectLineBreaks(String html) throws Exception {
        var message = message();
        sender.send(new OutboundEmail(WRITER, "Arbitrary\r\nsubject\rwith\nbreaks", "Text", html));
        assertThat(message.getSubject()).isEqualTo("Arbitrary  subject with breaks");
        verify(mail).send(message);
    }

    @Test
    @DisplayName("AC-001.1 rejects null subject and plain text before delivery")
    void rejectsNullRequiredContentBeforeDelivery() {
        assertThatThrownBy(() -> sender.send(new OutboundEmail(WRITER, null, "Text", null)))
                .isInstanceOf(InvalidOutboundEmailException.class)
                .hasMessage("Invalid outbound email")
                .hasNoCause();
        assertThatThrownBy(() -> sender.send(new OutboundEmail(WRITER, "Subject", null, null)))
                .isInstanceOf(InvalidOutboundEmailException.class)
                .hasMessage("Invalid outbound email")
                .hasNoCause();
        verifyNoInteractions(mail);
    }

    private MimeMessage message() {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(message);
        return message;
    }
}
