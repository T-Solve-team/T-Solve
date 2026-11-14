package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.worker.client.ReviewNotificationSender.Decision;

class ReviewNotificationSenderIT {

    @Test
    void deliversBothMultipartReviewNotificationsToTheWriter_AC_001_1_AC_001_2_AC_010_2_AC_010_7() {
        try (var mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.4"))
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025))) {
            mailpit.start();
            var factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(5));
            var inbox = RestClient.builder()
                    .baseUrl("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025))
                    .requestFactory(factory)
                    .build();

            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                    .withUserConfiguration(ReviewNotificationSender.class)
                    .withPropertyValues(
                            "spring.profiles.active=worker",
                            "spring.mail.host=" + mailpit.getHost(),
                            "spring.mail.port=" + mailpit.getMappedPort(1025),
                            "spring.mail.protocol=smtp",
                            "spring.mail.username=",
                            "spring.mail.password=",
                            "spring.mail.properties[mail.smtp.auth]=false",
                            "spring.mail.properties[mail.smtp.starttls.enable]=false",
                            "spring.mail.properties[mail.smtp.starttls.required]=false",
                            "TSOLVE_SMTP_FROM=review@example.test")
                    .run(context -> {
                        var sender = context.getBean(ReviewNotificationSender.class);
                        sender.send("writer@example.test", email(Decision.REJECT, "Thiếu bằng chứng"));
                        sender.send("writer@example.test", email(Decision.REQUEST_CHANGES, "Thêm bước tái hiện"));
                    });

            var expected = Map.of(
                    "[ITSUP-1] Your ticket was rejected", "Thiếu bằng chứng",
                    "[ITSUP-1] Changes requested for your ticket", "Thêm bước tái hiện");
            JsonNode messages = inbox.get()
                    .uri("/api/v1/messages")
                    .retrieve()
                    .body(JsonNode.class)
                    .required("messages");
            var subjects = new ArrayList<String>();
            for (JsonNode message : messages) {
                var subject = message.required("Subject").asText();
                subjects.add(subject);
                assertThat(expected).containsKey(subject);
                assertThat(message.required("From").required("Address").asText())
                        .isEqualTo("review@example.test");
                assertThat(message.required("From").required("Name").asText()).isEqualTo("T-Solve");
                var recipients = message.required("To");
                assertThat(recipients.size()).isEqualTo(1);
                assertThat(recipients.get(0).required("Address").asText()).isEqualTo("writer@example.test");
                JsonNode detail = inbox.get()
                        .uri("/api/v1/message/{id}", message.required("ID").asText())
                        .retrieve()
                        .body(JsonNode.class);
                assertThat(detail.required("Text").asText()).contains("ITSUP-1", expected.get(subject));
                assertThat(detail.required("HTML").asText())
                        .contains("ITSUP-1", expected.get(subject), "#5577FF", "https://acme.example.test/tickets/1");
                assertThat(detail.required("Text").asText()).contains("https://acme.example.test/tickets/1");
            }
            assertThat(subjects).containsExactlyInAnyOrderElementsOf(expected.keySet());
        }
    }

    private static ReviewEmail email(Decision decision, String feedback) {
        return new ReviewEmail(
                decision,
                "ITSUP-1",
                "Login fails",
                "ITSUP",
                "Reviewer",
                "Writer",
                Instant.parse("2026-11-14T18:42:00Z"),
                feedback,
                "https://acme.example.test/tickets/1",
                "acme.example.test");
    }
}
