package vn.tsolve.identity.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.security.SessionAuthenticationFilter;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.IdentityClock;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Checks every request that carries a session cookie against its session (AC-001.20 to .25, .27). Order: the host
 * decides whether a session counts at all (AC-001.22); a host or kind mismatch is MSG123 and keeps the session; then
 * the 8 h / 60 min limits (MSG07); then the Account re-read in the session's Workspace (gone: MSG07, Deactivated:
 * MSG10). A dead cookie is cleared and the request goes on anonymous, so a protected endpoint answers MSG07 and a
 * public one is unaffected. No cookie: nothing happens and Redis is not called. The web chain adds it (Boot's own
 * servlet registration is disabled in {@code SessionConfig}).
 */
public class WebSessionFilter extends OncePerRequestFilter implements SessionAuthenticationFilter {

    /** The header a background request sends; only the value {@code 1} makes it background (AC-001.25). */
    public static final String BACKGROUND_HEADER = "X-TSolve-Background";

    private static final Logger log = LoggerFactory.getLogger(WebSessionFilter.class);

    /** The result of the check: the user (null = anonymous), or {@code answered} when the filter wrote the answer. */
    private record Outcome(TSolveUser user, boolean answered) {
        static final Outcome ANONYMOUS = new Outcome(null, false);
        static final Outcome ANSWERED = new Outcome(null, true);
    }

    private final WebSessionStore store;
    private final SessionCookie cookies;
    private final AuthHosts hosts;
    private final WorkspaceLookup workspaces;
    private final TenantScope scope;
    private final AccountRepository accounts;
    private final IdentityClock clock;
    private final SecurityProblemWriter problems;

    public WebSessionFilter(
            WebSessionStore store,
            SessionCookie cookies,
            AuthHosts hosts,
            WorkspaceLookup workspaces,
            TenantScope scope,
            AccountRepository accounts,
            IdentityClock clock,
            SecurityProblemWriter problems) {
        this.store = store;
        this.cookies = cookies;
        this.hosts = hosts;
        this.workspaces = workspaces;
        this.scope = scope;
        this.accounts = accounts;
        this.clock = clock;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cookie = cookies.valueOf(request);
        if (cookie == null || cookie.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Outcome outcome;
        try {
            outcome = check(request, response, cookie);
        } catch (DataAccessException e) {
            // class name only: the message can carry the host, the key or the account
            log.error(
                    "Session check failed in the session store or the database ({})",
                    e.getClass().getName());
            problems.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, SecurityProblemWriter.MSG06);
            return;
        }
        if (outcome.answered()) {
            return;
        }
        TSolveUser user = outcome.user();
        if (user != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
            SecurityContextHolder.setContext(context);
            // SecurityContextHolderFilter reloads from this repository on an ASYNC re-dispatch (SseEmitter, Callable)
            new RequestAttributeSecurityContextRepository().saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private Outcome check(HttpServletRequest request, HttpServletResponse response, String cookie) throws IOException {
        boolean admin = hosts.isAdminHost(request);
        Optional<WorkspaceRef> site = admin ? Optional.empty() : workspaces.findBySiteHost(hosts.hostOf(request));
        if (!admin && site.isEmpty()) {
            return Outcome.ANONYMOUS; // login., api. or an unknown host: no session counts there
        }
        Optional<WebSession> found = store.find(cookie);
        if (found.isEmpty()) {
            clear(response);
            return Outcome.ANONYMOUS;
        }
        WebSession session = found.get();
        // shortcut: an OPERATOR session is refused everywhere until Task 29 builds the Console side
        if (admin
                || session.kind() != WebSession.Kind.WORKSPACE
                || !site.get().id().equals(session.workspaceId())) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG123);
            return Outcome.ANSWERED;
        }
        Instant now = clock.now();
        if (Duration.between(session.lastActivityAt(), now).compareTo(WebSessionStore.IDLE_LIMIT) > 0
                || Duration.between(session.createdAt(), now).compareTo(WebSessionStore.ABSOLUTE_LIMIT) > 0) {
            end(response, cookie);
            return Outcome.ANONYMOUS;
        }
        Account account = session.accountId() == null
                ? null
                : scope.runIn(session.workspaceId(), () -> accounts.findById(session.accountId())
                        .orElse(null));
        if (account == null) {
            end(response, cookie);
            return Outcome.ANONYMOUS;
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            end(response, cookie);
            problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG10);
            return Outcome.ANSWERED;
        }
        if (!"1".equals(request.getHeader(BACKGROUND_HEADER))) {
            store.touch(cookie, now);
        }
        // Roles come with Task 26b; until then the user holds the implicit Employee role only
        String fullName = account.getFullName() == null ? "" : account.getFullName();
        return new Outcome(new TSolveUser(account.getId(), account.getEmail(), fullName, Set.of()), false);
    }

    private void end(HttpServletResponse response, String cookie) {
        store.end(cookie);
        clear(response);
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire().toString());
    }
}
