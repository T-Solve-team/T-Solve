package vn.tsolve.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Properties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.worker.client.SmtpEmailSender;

/** One Mailpit per test JVM, shared by SMTP and outbox integration tests. */
public final class WorkerMailpit {
    private final GenericContainer<?> container;
    private final RestClient inbox;

    private WorkerMailpit() {
        container = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.4"))
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));
        container.start();
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        inbox = RestClient.builder()
                .baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(8025))
                .requestFactory(factory)
                .build();
    }

    public static WorkerMailpit instance() {
        return Holder.INSTANCE;
    }

    public SmtpEmailSender sender() {
        var mail = new JavaMailSenderImpl();
        mail.setHost(container.getHost());
        mail.setPort(container.getMappedPort(1025));
        mail.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
        mail.getJavaMailProperties().put("mail.smtp.timeout", "3000");
        mail.getJavaMailProperties().put("mail.smtp.writetimeout", "5000");
        return new SmtpEmailSender(mail, "review@example.test");
    }

    public void clear() {
        inbox.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
    }

    public JsonNode messages() {
        return inbox.get()
                .uri("/api/v1/messages")
                .retrieve()
                .body(JsonNode.class)
                .required("messages");
    }

    public MimeMessage onlyMessage() throws Exception {
        JsonNode messages = messages();
        assertThat(messages.size()).isEqualTo(1);
        byte[] raw = inbox.get()
                .uri("/api/v1/message/{id}/raw", messages.get(0).required("ID").asText())
                .retrieve()
                .body(byte[].class);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    private static final class Holder {
        private static final WorkerMailpit INSTANCE = new WorkerMailpit();
    }
}
