package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import vn.tsolve.worker.client.ReviewNotificationSender.Decision;
import vn.tsolve.worker.client.ReviewNotificationSender.DeliveryException;

@ExtendWith(OutputCaptureExtension.class)
class ReviewNotificationSenderTest {

    private static final String WRITER = "writer@example.test";
    private static final String FROM = "review@example.test";
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final ReviewNotificationSender sender = new ReviewNotificationSender(mail, FROM);

    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(ReviewNotificationSender.class);

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(Decision.class)
    void sendsBrandedMultipartAndDecisionSubject_AC_001_1_AC_001_2_AC_010_2_AC_010_7(Decision decision)
            throws Exception {
        when(mail.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        var email = ReviewEmailRendererTest.email(decision, "acme.example.test", "Linh", "Login fails", "Add evidence");
        sender.send(WRITER, email);
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        var message = captor.getValue();
        message.saveChanges();
        assertThat(message.getSubject())
                .isEqualTo(
                        decision == Decision.REJECT
                                ? "[ITS-1] Your ticket was rejected"
                                : "[ITS-1] Changes requested for your ticket");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("T-Solve");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo(FROM);
        assertThat(message.getAllRecipients()).hasSize(1);
        var parts = new ArrayList<Part>();
        collectParts(message, parts);
        assertThat(parts)
                .anySatisfy(part -> {
                    assertThat(part.isMimeType("text/plain")).isTrue();
                    assertThat(part.getContent().toString()).contains("Add evidence", email.ticketUrl());
                })
                .anySatisfy(part -> {
                    assertThat(part.isMimeType("text/html")).isTrue();
                    assertThat(part.getContent().toString()).contains("#5577FF", "Add evidence", email.ticketUrl());
                });
    }

    @Test
    void replacesSubjectLineBreaks_AC_001_1_AC_001_2_AC_010_9() throws Exception {
        when(mail.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        var original = ReviewEmailRendererTest.email(Decision.REJECT, null, null, "Title", "Reason");
        sender.send(
                WRITER,
                new ReviewEmail(
                        original.decision(),
                        "ITS\r\n-1",
                        original.ticketTitle(),
                        original.projectName(),
                        original.reviewerName(),
                        original.writerName(),
                        original.decidedAt(),
                        original.feedback(),
                        null,
                        null));
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("[ITS  -1] Your ticket was rejected");
    }

    private static void collectParts(Part part, List<Part> parts) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collectParts(child, parts);
            }
        } else {
            parts.add(part);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "writer@example.test,other@example.test",
                "team:writer@example.test;",
                "writer@example.test\r\nBcc: other@example.test"
            })
    void rejectsAnythingOtherThanOneMailboxBeforeDelivery_AC_001_3(String recipient) {
        assertThatThrownBy(() -> sender.send(
                        recipient,
                        ReviewEmailRendererTest.email(Decision.REJECT, null, null, "Title", "Needs evidence")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mail);
    }

    @Test
    void reportsOneSafeFailureWithoutLoggingMailData_AC_001_4_AC_001_6(CapturedOutput output) {
        String feedback = "private-review-feedback";
        String diagnostic = WRITER + " " + feedback + " sample-smtp-credential";
        when(mail.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException(diagnostic)).when(mail).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(
                        WRITER, ReviewEmailRendererTest.email(Decision.REJECT, null, null, "Title", feedback)))
                .isInstanceOf(DeliveryException.class)
                .hasMessage("Review email delivery failed")
                .hasNoCause();
        verify(mail).send(any(MimeMessage.class));
        assertThat(output.getAll()).doesNotContain(WRITER, feedback, "sample-smtp-credential");
    }

    @Test
    void configuresFiniteMailTimeouts_AC_001_5() {
        context.withPropertyValues(
                        "spring.profiles.active=worker", "spring.mail.host=localhost", "TSOLVE_SMTP_FROM=" + FROM)
                .run(web -> {
                    assertThat(web).hasSingleBean(ReviewNotificationSender.class);
                    assertThat(web.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "5000")
                            .containsEntry("mail.smtp.timeout", "3000")
                            .containsEntry("mail.smtp.writetimeout", "5000");
                });
    }

    @Test
    void webContextStartsWithoutSmtpSettings_AC_001_7() {
        context.run(web -> assertThat(web)
                .hasNotFailed()
                .doesNotHaveBean(JavaMailSender.class)
                .doesNotHaveBean(ReviewNotificationSender.class));
    }
}
