package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import vn.tsolve.worker.client.SmtpEmailSender;

class SmtpMailConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(application -> {
                var environment = application.getEnvironment();
                var sources = environment.getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                environment.setActiveProfiles("worker");
                try {
                    for (var source : new YamlPropertySourceLoader()
                            .load("application.yml", new ClassPathResource("application.yml"))) {
                        sources.addLast(source);
                    }
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            })
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(SmtpEmailSender.class);

    @Test
    void bindsDotEnvMailPropertiesAndCreatesSender_BUG_245() {
        // Plain properties deliberately have no SystemEnvironmentPropertySource relaxed-name lookup.
        context.withPropertyValues(
                        "SPRING_MAIL_HOST=smtp.example.test",
                        "SPRING_MAIL_PORT=587",
                        "SPRING_MAIL_USERNAME=review",
                        "SPRING_MAIL_PASSWORD=test-password",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY=true",
                        "TSOLVE_SMTP_FROM=review@example.test")
                .run(application -> {
                    assertThat(application).hasNotFailed().hasSingleBean(SmtpEmailSender.class);
                    assertThat(application.getEnvironment().getProperty("spring.mail.host"))
                            .isEqualTo("smtp.example.test");
                    var mail = application.getBean(JavaMailSenderImpl.class);
                    assertThat(mail.getHost()).isEqualTo("smtp.example.test");
                    assertThat(mail.getPort()).isEqualTo(587);
                    assertThat(mail.getUsername()).isEqualTo("review");
                    assertThat(mail.getPassword()).isEqualTo("test-password");
                    assertThat(mail.getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "true")
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.starttls.required", "true")
                            .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                            .containsEntry("mail.smtp.connectiontimeout", "5000")
                            .containsEntry("mail.smtp.timeout", "10000")
                            .containsEntry("mail.smtp.writetimeout", "5000");
                });
    }

    @Test
    void keepsExportedEnvironmentMailConfiguration_BUG_245() {
        context.withInitializer(application -> application
                        .getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                "stagingEnvironment",
                                Map.of(
                                        "SPRING_MAIL_HOST", "smtp.example.test",
                                        "SPRING_MAIL_PORT", "2525",
                                        "TSOLVE_SMTP_FROM", "review@example.test",
                                        "SMTP_READ_TIMEOUT_MS", "15000"))))
                .run(application -> {
                    assertThat(application).hasNotFailed().hasSingleBean(SmtpEmailSender.class);
                    var mail = application.getBean(JavaMailSenderImpl.class);
                    assertThat(mail.getHost()).isEqualTo("smtp.example.test");
                    assertThat(mail.getPort()).isEqualTo(2525);
                    assertThat(mail.getJavaMailProperties()).containsEntry("mail.smtp.timeout", "15000");
                });
    }

    @Test
    void leavesSenderUnconfiguredWithoutHost_BUG_245() {
        context.run(application -> assertThat(application).hasNotFailed().doesNotHaveBean(SmtpEmailSender.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void leavesSenderUnconfiguredForBlankHost_BUG_245(String host) {
        context.withPropertyValues("SPRING_MAIL_HOST=" + host)
                .run(application -> assertThat(application).hasNotFailed().doesNotHaveBean(SmtpEmailSender.class));
    }

    @Test
    void keepsLibraryMailDefaultsWithoutPropertyOverrides_BUG_245() {
        context.withPropertyValues("SPRING_MAIL_HOST=smtp.example.test", "TSOLVE_SMTP_FROM=review@example.test")
                .run(application -> {
                    assertThat(application).hasNotFailed().hasSingleBean(SmtpEmailSender.class);
                    assertThat(application.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false")
                            .containsEntry("mail.smtp.starttls.required", "false")
                            .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
                });
    }

    @Test
    void usesDefaultPortForConfiguredHost_BUG_245() {
        context.withPropertyValues("SPRING_MAIL_HOST=smtp.example.test", "TSOLVE_SMTP_FROM=review@example.test")
                .run(application -> {
                    assertThat(application).hasNotFailed().hasSingleBean(SmtpEmailSender.class);
                    assertThat(application.getBean(JavaMailSenderImpl.class).getPort())
                            .isEqualTo(25);
                });
    }
}
