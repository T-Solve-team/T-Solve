package vn.tsolve.identity.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.SecurityProblemWriter;

/**
 * {@code POST /api/v1/auth/logout}: ends the Redis session, expires the cookie, publishes {@link SessionEnded}
 * (LOGOUT) once and answers {@code { logoutUrl }} (AC-002.1, .2, .3, .6, .8). Only the caller whose DEL removed the key
 * publishes; a caller that lost that race still answers 200 with the same URL and publishes nothing. The session
 * filter has already refused a missing, unknown or expired session (401 MSG07) and CSRF has been checked.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

    private final WebSessionStore store;
    private final SessionCookie cookies;
    private final KeycloakLogoutUrlBuilder urls;
    private final ApplicationEventPublisher events;
    private final SecurityProblemWriter problems;

    public LogoutController(
            WebSessionStore store,
            SessionCookie cookies,
            KeycloakLogoutUrlBuilder urls,
            ApplicationEventPublisher events,
            ObjectMapper mapper) {
        this.store = store;
        this.cookies = cookies;
        this.urls = urls;
        this.events = events;
        this.problems = new SecurityProblemWriter(mapper);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String cookie = cookies.valueOf(request);
        Optional<WebSession> session = cookie == null || cookie.isEmpty() ? Optional.empty() : store.find(cookie);
        if (session.isEmpty()) {
            // the session ended between the filter and here: same answer as the filter gives
            problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG07);
            return null;
        }
        String logoutUrl = urls.build(session.get(), request).toString();
        boolean claimed = store.end(cookie);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire().toString());
        if (claimed && session.get().kind() == WebSession.Kind.WORKSPACE) {
            events.publishEvent(new SessionEnded(
                    session.get().accountId(),
                    session.get().workspaceId(),
                    store.sessionIdOf(cookie),
                    SessionEnded.Reason.LOGOUT));
        }
        return Map.of("logoutUrl", logoutUrl);
    }
}
