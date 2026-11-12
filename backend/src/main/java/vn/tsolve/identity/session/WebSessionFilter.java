package vn.tsolve.identity.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.security.SessionAuthenticationFilter;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountRole;
import vn.tsolve.identity.account.AccountRoleRepository;
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
 * public one is unaffected. An Operator session (admin. only) goes: host, then the same limits (MSG07, also when the
 * email is off the allow-list), then the allow-list read again (MSG135), then the Console list (every request off it
 * is MSG08, whatever the spelling of its path); it ends without a {@code SessionEnded} event, since it has no Account
 * or Workspace. No cookie: nothing happens and Redis is not called. The web chain adds it (Boot's own
 * servlet registration is disabled in {@code SessionConfig}).
 */
public class WebSessionFilter extends OncePerRequestFilter implements SessionAuthenticationFilter {

    /** The header a background request sends; only the value {@code 1} makes it background (AC-001.25). */
    public static final String BACKGROUND_HEADER = "X-TSolve-Background";

    private static final Logger log = LoggerFactory.getLogger(WebSessionFilter.class);

    /** The result of the check: the user (null = anonymous), or {@code answered} when the filter wrote the answer. */
    private record Outcome(TSolveUser user, UUID workspaceId, OperatorPrincipal operator, boolean answered) {
        static final Outcome ANONYMOUS = new Outcome(null, null, null, false);
        static final Outcome ANSWERED = new Outcome(null, null, null, true);
    }

    /** Carries an IOException / ServletException of the chain through {@code TenantScope.runIn}'s Supplier. */
    private static final class ChainFailure extends RuntimeException {
        ChainFailure(Exception cause) {
            super(cause);
        }
    }

    private final WebSessionStore store;
    private final SessionCookie cookies;
    private final AuthHosts hosts;
    private final WorkspaceLookup workspaces;
    private final TenantScope scope;
    private final AccountRepository accounts;
    private final AccountRoleRepository roleRows;
    private final IdentityClock clock;
    private final ApplicationEventPublisher events;
    private final SecurityProblemWriter problems;
    private final OperatorAllowList operatorList;
    private final ConsolePaths consolePaths;

    public WebSessionFilter(
            WebSessionStore store,
            SessionCookie cookies,
            AuthHosts hosts,
            WorkspaceLookup workspaces,
            TenantScope scope,
            AccountRepository accounts,
            AccountRoleRepository roleRows,
            IdentityClock clock,
            ApplicationEventPublisher events,
            SecurityProblemWriter problems,
            OperatorAllowList operatorList,
            ConsolePaths consolePaths) {
        this.events = events;
        this.operatorList = operatorList;
        this.consolePaths = consolePaths;
        this.roleRows = roleRows;
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
        if (outcome.operator() != null) {
            // no Workspace is bound for an Operator (AC-001.29)
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(outcome.operator(), null, List.of()));
            SecurityContextHolder.setContext(context);
            new RequestAttributeSecurityContextRepository().saveContext(context, request, response);
            chain.doFilter(request, response);
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
            // the session's Workspace stays bound for the whole request (AC-001.76); runIn unbinds in its finally
            try {
                scope.runIn(outcome.workspaceId(), () -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        throw new ChainFailure(e);
                    }
                    return null;
                });
            } catch (ChainFailure e) {
                if (e.getCause() instanceof IOException io) {
                    throw io;
                }
                throw (ServletException) e.getCause();
            }
            return;
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
        // the host decides first: an Operator session counts on admin. only, a Workspace session on its own site only
        boolean operator = session.kind() == WebSession.Kind.OPERATOR;
        if (admin != operator || (!admin && !site.get().id().equals(session.workspaceId()))) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG123);
            return Outcome.ANSWERED;
        }
        Instant now = clock.now();
        if (Duration.between(session.lastActivityAt(), now).compareTo(WebSessionStore.IDLE_LIMIT) > 0
                || Duration.between(session.createdAt(), now).compareTo(WebSessionStore.ABSOLUTE_LIMIT) > 0) {
            end(response, cookie, session, SessionEnded.Reason.EXPIRED);
            return Outcome.ANONYMOUS;
        }
        if (operator) {
            // AC-001.33: the list is read again at every request, background ones too
            if (!operatorList.contains(session.operatorEmail())) {
                end(response, cookie, session, SessionEnded.Reason.DEACTIVATED);
                problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG135);
                return Outcome.ANSWERED;
            }
            // edge 141: an Operator session reaches the Console endpoints only. Fail closed: no test on the spelling of
            // the path (a percent-encoded "/%61pi/" must not slip past), so anything off the Console list is refused.
            // The backend serves no page or static file; the Console page comes from the front end's own host.
            if (!consolePaths.allows(request.getMethod(), request.getRequestURI())) {
                problems.write(request, response, HttpStatus.FORBIDDEN, SecurityProblemWriter.MSG08);
                return Outcome.ANSWERED;
            }
            if (!"1".equals(request.getHeader(BACKGROUND_HEADER))) {
                store.touch(cookie, now);
            }
            return new Outcome(null, null, new OperatorPrincipal(session.operatorEmail()), false);
        }
        Account account = null;
        Set<Role> roles = Set.of();
        if (session.accountId() != null) {
            // one scope for the Account and its Roles, read again at every request so a Role change applies next
            account = scope.runIn(session.workspaceId(), () -> accounts.findById(session.accountId())
                    .orElse(null));
            if (account != null) {
                roles = scope.runIn(session.workspaceId(), () -> roleRows.findByUserId(session.accountId()).stream()
                        .map(AccountRole::role)
                        .collect(Collectors.toSet()));
            }
        }
        if (account == null) {
            end(response, cookie, session, SessionEnded.Reason.DEACTIVATED);
            return Outcome.ANONYMOUS;
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            end(response, cookie, session, SessionEnded.Reason.DEACTIVATED);
            problems.write(request, response, HttpStatus.UNAUTHORIZED, SecurityProblemWriter.MSG10);
            return Outcome.ANSWERED;
        }
        if (!"1".equals(request.getHeader(BACKGROUND_HEADER))) {
            store.touch(cookie, now);
        }
        String fullName = account.getFullName() == null ? "" : account.getFullName();
        return new Outcome(
                new TSolveUser(account.getId(), account.getEmail(), fullName, roles),
                session.workspaceId(),
                null,
                false);
    }

    /**
     * Ends the session and publishes the event only if this request won the claim, after the key is gone. An Operator
     * session ends silently: an Operator has no Account or Workspace for the event to name.
     */
    private void end(HttpServletResponse response, String cookie, WebSession session, SessionEnded.Reason reason) {
        boolean claimed = store.end(cookie);
        clear(response);
        if (claimed && session.kind() != WebSession.Kind.OPERATOR) {
            events.publishEvent(
                    new SessionEnded(session.accountId(), session.workspaceId(), store.sessionIdOf(cookie), reason));
        }
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire().toString());
    }
}
