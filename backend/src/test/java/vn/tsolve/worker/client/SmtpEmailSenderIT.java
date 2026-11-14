package vn.tsolve.worker.client;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.worker.WorkerMailpit;
import vn.tsolve.worker.dto.OutboundEmail;

class SmtpEmailSenderIT {
    @Test
    @DisplayName("AC-001.1 sends one plain-text message from the configured address")
    void sendsPlainText() throws Exception {
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();
        String body = "Thiếu bằng chứng\r\n";
        mailpit.sender().send(new OutboundEmail("writer@example.test", "Thông báo", body, null));
        var message = mailpit.onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Thông báo");
        assertThat(message.getFrom()[0].toString()).isEqualTo("review@example.test");
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("writer@example.test");
        assertThat(message.isMimeType("text/plain")).isTrue();
        assertThat(message.getContent()).isEqualTo(body);
        assertThat(message.getContentType()).containsIgnoringCase("charset=UTF-8");
    }

    @Test
    @DisplayName("AC-001.2 sends unchanged Unicode plain text and HTML as multipart alternatives")
    void sendsAlternatives() throws Exception {
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();
        String text = "Thêm bước tái hiện — 日本語";
        String html = "<p>Thêm bước tái hiện — 日本語</p>";
        mailpit.sender().send(new OutboundEmail("writer@example.test", "Thông báo", text, html));
        var message = mailpit.onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Thông báo");
        Multipart alternative = alternatives(message);
        assertThat(alternative).isNotNull();
        assertThat(alternative.getCount()).isEqualTo(2);
        assertThat(alternative.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(alternative.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat(alternative.getBodyPart(0).getContent()).isEqualTo(text);
        assertThat(alternative.getBodyPart(1).getContent()).isEqualTo(html);
        for (int i = 0; i < 2; i++) {
            assertThat(alternative.getBodyPart(i).getContentType()).containsIgnoringCase("charset=UTF-8");
        }
    }

    private static Multipart alternatives(Part part) throws Exception {
        if (part.isMimeType("multipart/alternative")) {
            return (Multipart) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Multipart found = alternatives(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
