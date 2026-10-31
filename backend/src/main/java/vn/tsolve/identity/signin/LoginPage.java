package vn.tsolve.identity.signin;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

/**
 * The minimal {@code login.} page (AC-001.39): the exact Report3 text of one message, no link, no script, so no
 * Workspace site is reachable from it and the web app is not loaded.
 */
public final class LoginPage {

    /** Report3 texts. The other messages the page shows (MSG06, 09, 10, 75, 121, 122) come with the tasks that use them. */
    private static final Map<String, String> TEXTS =
            Map.of("MSG141", "This sign-in request is not valid or has expired. Please start the sign-in again.");

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'";

    private LoginPage() {}

    public static ResponseEntity<String> response(HttpStatus status, String msgCode) {
        String text = TEXTS.get(msgCode);
        if (text == null) {
            throw new IllegalArgumentException("No page text for " + msgCode);
        }
        String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>T-Solve</title>"
                + "<style>body{font-family:sans-serif;margin:4rem auto;max-width:32rem;padding:0 1rem;}</style>"
                + "</head><body><p>" + HtmlUtils.htmlEscape(text) + "</p></body></html>";
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .cacheControl(CacheControl.noStore())
                .body(html);
    }
}
