package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.springframework.mail.SimpleMailMessage;
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
    @ValueSource(
            strings = {
                "",
                "writer@example.test,other@example.test",
                "team:writer@example.test;",
                "writer@example.test\r\nBcc: other@example.test"
            })
    void rejectsAnythingOtherThanOneMailboxBeforeDelivery_AC_001_3(String recipient) {
        assertThatThrownBy(() -> sender.send(Decision.REJECT, recipient, "ITSUP-1", "Needs evidence"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mail);
    }

    @Test
    void reportsOneSafeFailureWithoutLoggingMailData_AC_001_4_AC_001_6(CapturedOutput output) {
        String feedback = "private-review-feedback";
        String diagnostic = WRITER + " " + feedback + " sample-smtp-credential";
        doThrow(new MailSendException(diagnostic)).when(mail).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send(Decision.REJECT, WRITER, "ITSUP-1", feedback))
                .isInstanceOf(DeliveryException.class)
                .hasMessage("Review email delivery failed")
                .hasNoCause();
        verify(mail).send(any(SimpleMailMessage.class));
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
