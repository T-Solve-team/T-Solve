package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountRoleRepository;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.IdentityClock;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/**
 * The filter binds the session's Workspace in the one holder for the whole request and leaves none behind (Task 6a-1,
 * AC-002.11 holder side). Built by hand over the real holder and {@code PlatformTenantScope}; it does not import
 * {@code TwoTenantDatabases}, whose {@code @Primary RoutingTenantScope} would replace the scope under test.
 */
class WebSessionFilterTenantContextTest {

    private static final UUID A = InMemoryWorkspaceLookup.ACME.id();
    private static final String COOKIE = "session-cookie-value";
    private static final Instant NOW = Instant.parse("2026-11-22T08:00:00Z");

    private final InMemoryWorkspaceLookup workspaces = new InMemoryWorkspaceLookup();
    private final SignInConfig config = new SignInConfig();
    private final TenantContext holder = new ThreadLocalTenantContext(config.knownWorkspaces(workspaces));
    private final WebSessionStore store = mock(WebSessionStore.class);
    private final SessionCookie cookies = mock(SessionCookie.class);
    private final AuthHosts hosts = mock(AuthHosts.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountRoleRepository roleRows = mock(AccountRoleRepository.class);

    private WebSessionFilter filter;

    @BeforeEach
    void buildTheFilter() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account("user@acme.example", "Acme User", AccountStatus.ACTIVE, NOW);
        ReflectionTestUtils.setField(account, "id", accountId);
        when(hosts.hostOf(org.mockito.ArgumentMatchers.any())).thenReturn("acme.localhost");
        when(hosts.isAdminHost(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(store.find(COOKIE))
                .thenReturn(Optional.of(new WebSession(
                        WebSession.Kind.WORKSPACE, A, accountId, null, "acme.localhost", "hint", NOW, NOW)));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(roleRows.findByUserId(accountId)).thenReturn(List.of());
        filter = new WebSessionFilter(
                store,
                cookies,
                hosts,
                workspaces,
                config.tenantScope(holder),
                accounts,
                roleRows,
                new IdentityClock(Clock.fixed(NOW, ZoneOffset.UTC)),
                mock(ApplicationEventPublisher.class),
                mock(SecurityProblemWriter.class),
                mock(OperatorAllowList.class),
                mock(ConsolePaths.class));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    private void withCookie(String value) {
        when(cookies.valueOf(org.mockito.ArgumentMatchers.any())).thenReturn(value);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setServerName("acme.localhost");
        return request;
    }

    @Test
    @DisplayName("AC-002.11 a request bound by WebSessionFilter is seen by TenantContext, and none is bound after it")
    void aBoundRequestIsSeenByTheHolderAndUnboundAfterwards() throws Exception {
        withCookie(COOKIE);
        AtomicReference<Optional<UUID>> seenByTheHandler = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenByTheHandler.set(holder.currentWorkspaceId());

        filter.doFilter(request(), new MockHttpServletResponse(), chain);

        assertThat(seenByTheHandler.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    static Stream<Exception> chainFailures() {
        return Stream.of(
                new IllegalStateException("handler failed"),
                new ServletException("servlet failed"),
                new IOException("io failed"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chainFailures")
    @DisplayName("AC-002.11 edge 2 after the handler throws, the next request on the same thread sees no Workspace")
    void afterTheHandlerThrowsTheNextRequestSeesNoWorkspace(Exception failure) throws Exception {
        withCookie(COOKIE);
        AtomicReference<Optional<UUID>> seenByFailingHandler = new AtomicReference<>();
        FilterChain failing = (req, res) -> {
            seenByFailingHandler.set(holder.currentWorkspaceId());
            if (failure instanceof ServletException servlet) throw servlet;
            if (failure instanceof IOException io) throw io;
            throw (RuntimeException) failure;
        };

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), failing))
                .isSameAs(failure);
        assertThat(seenByFailingHandler.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        // The next request on this thread carries no cookie: nothing binds, and nothing is left from the first.
        withCookie(null);
        AtomicReference<Optional<UUID>> seenByNext = new AtomicReference<>();
        AtomicReference<Throwable> requireOutcome = new AtomicReference<>();
        FilterChain next = (req, res) -> {
            seenByNext.set(holder.currentWorkspaceId());
            try {
                holder.requireWorkspaceId();
            } catch (RuntimeException e) {
                requireOutcome.set(e);
            }
        };
        filter.doFilter(request(), new MockHttpServletResponse(), next);

        assertThat(seenByNext.get()).isNotNull().isEmpty();
        assertThat(requireOutcome.get()).isInstanceOf(NoTenantException.class);
    }
}
