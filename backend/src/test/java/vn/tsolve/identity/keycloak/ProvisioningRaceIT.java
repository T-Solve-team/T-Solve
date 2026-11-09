package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.ACME_CALLBACK;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.ADMIN_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.BETA_CALLBACK;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.callbackUri;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.domainOf;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.logoutUri;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.newSlug;

import ch.qos.logback.classic.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;

/**
 * Two operations at once (AC-001.59, edge 107). Deterministic: a Mockito spy of the real {@link KeycloakAdminClient}
 * pauses the first call inside its URI write, so the test proves the lock, not luck. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44}.
 */
class ProvisioningRaceIT {

    private static final String[] MUTATING = {"create", "write", "delete", "update", "add", "remove"};

    private static KeycloakProvisioningFixture fixture;
    private static ExecutorService executor;

    @BeforeAll
    static void start() {
        fixture = new KeycloakProvisioningFixture();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (fixture != null) {
            fixture.close();
        }
    }

    enum Second {
        PROVISION_OTHER_SLUG,
        REMOVE_OTHER_WORKSPACE
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Second.class)
    @DisplayName(
            "AC-001.59 edge 107 provision and remove run one at a time, and two slugs at once keep all four URIs")
    void runsOneAtATimeAndKeepsAllFourUris_AC_001_59_edge107(Second second) throws Exception {
        String slugA = newSlug();
        String slugB = newSlug();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        KeycloakAdminClient client = spy(fixture.newAdminClient());
        AtomicBoolean armed = new AtomicBoolean(false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    if (armed.compareAndSet(true, false)) {
                        entered.countDown();
                        assertThat(release.await(60, TimeUnit.SECONDS)).isTrue();
                    }
                    return invocation.callRealMethod();
                })
                .when(client)
                .writeWebClientUris(any());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);

        if (second == Second.REMOVE_OTHER_WORKSPACE) {
            provisioner.provision(idB, slugB, domainOf(slugB)); // not paused: the latch is not armed yet
        }
        KeycloakProvisioningFixture.Snapshot beforeFirst = fixture.snapshot();

        armed.set(true);
        CompletableFuture<?> first =
                CompletableFuture.runAsync(() -> provisioner.provision(idA, slugA, domainOf(slugA)), executor);
        assertThat(entered.await(60, TimeUnit.SECONDS)).as("the first call reached its URI write").isTrue();
        long writesWhenPaused = mutatingCalls(client);

        CompletableFuture<?> secondCall = CompletableFuture.runAsync(
                () -> {
                    if (second == Second.PROVISION_OTHER_SLUG) {
                        provisioner.provision(idB, slugB, domainOf(slugB));
                    } else {
                        provisioner.remove(idB);
                    }
                },
                executor);

        Thread.sleep(2000);
        assertThat(secondCall).as("the second call waits for the lock").isNotDone();
        assertThat(mutatingCalls(client))
                .as("the second call made no Keycloak write while the first holds the lock")
                .isEqualTo(writesWhenPaused);
        assertThat(fixture.redirectUris()).containsExactlyInAnyOrderElementsOf(beforeFirst.redirectUris());
        assertThat(fixture.logoutUris()).containsExactlyInAnyOrderElementsOf(beforeFirst.logoutUris());

        release.countDown();
        first.get(60, TimeUnit.SECONDS);
        secondCall.get(60, TimeUnit.SECONDS);

        List<String> redirect = fixture.redirectUris();
        List<String> logout = fixture.logoutUris();
        assertThat(redirect).contains(ACME_CALLBACK, BETA_CALLBACK, callbackUri(slugA));
        assertThat(logout).contains(logoutUri(slugA));
        if (second == Second.PROVISION_OTHER_SLUG) {
            assertThat(redirect).contains(callbackUri(slugB));
            assertThat(logout).contains(logoutUri(slugB));
        } else {
            assertThat(redirect).doesNotContain(callbackUri(slugB));
            assertThat(logout).doesNotContain(logoutUri(slugB));
        }
    }

    @Test
    @DisplayName("AC-001.59 edge 107 the re-read after a URI write detects a lost earlier URI, throws and undoes")
    void reReadDetectsLostUri_AC_001_59_edge107() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        List<String> redirectBefore = fixture.redirectUris();
        List<String> logoutBefore = fixture.logoutUris();

        KeycloakAdminClient client = spy(fixture.newAdminClient());
        AtomicBoolean done = new AtomicBoolean(false);
        // right after the adapter's write and before its re-read, someone edits tsolve-web without the lock
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    if (done.compareAndSet(false, true)) {
                        List<String> lost = new ArrayList<>(fixture.redirectUris());
                        lost.remove(ACME_CALLBACK);
                        fixture.setWebClientUris(lost, fixture.logoutUris());
                    }
                    return result;
                })
                .when(client)
                .writeWebClientUris(any());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            assertThatThrownBy(() -> provisioner.provision(workspaceId, slug, domain))
                    .isInstanceOf(RuntimeException.class);

            assertThat(fixture.organizationsWithDomain(domain)).isEmpty();
            assertThat(fixture.identityProvider("google-" + slug)).isEmpty();
            assertThat(fixture.redirectUris()).doesNotContain(callbackUri(slug));
            assertThat(fixture.logoutUris()).doesNotContain(logoutUri(slug));
            // Q7: the lost URI is not put back (the runbook restores it)
            assertThat(fixture.redirectUris()).doesNotContain(ACME_CALLBACK);
            assertThat(logs.lines(Level.ERROR))
                    .as("one ERROR that names the lost URI")
                    .hasSize(1)
                    .allSatisfy(line -> assertThat(line).contains(ACME_CALLBACK));
        } finally {
            // the test's own repair of the shared realm, so the other tests of this class see acme again
            fixture.setWebClientUris(redirectBefore, logoutBefore);
        }
    }

    /** Someone edits tsolve-web in the admin console, without the lock, and drops the acme callback. */
    private static void dropAcmeCallback() {
        List<String> lost = new ArrayList<>(fixture.redirectUris());
        lost.remove(ACME_CALLBACK);
        fixture.setWebClientUris(lost, fixture.logoutUris());
    }

    @Test
    @DisplayName(
            "AC-001.59 B1 remove re-reads tsolve-web after its URI write, throws on a lost earlier URI, logs one ERROR naming it and repairs nothing")
    void removeDetectsLostUri_AC_001_59_B1() {
        String slug = newSlug();
        UUID workspaceId = UUID.randomUUID();
        List<String> redirectBefore = fixture.redirectUris();
        List<String> logoutBefore = fixture.logoutUris();

        KeycloakAdminClient client = spy(fixture.newAdminClient());
        AtomicBoolean armed = new AtomicBoolean(false);
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    if (armed.compareAndSet(true, false)) {
                        dropAcmeCallback();
                    }
                    return result;
                })
                .when(client)
                .writeWebClientUris(any());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);
        provisioner.provision(workspaceId, slug, domainOf(slug)); // not armed yet

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            armed.set(true);
            assertThatThrownBy(() -> provisioner.remove(workspaceId))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(ADMIN_SECRET));

            assertThat(fixture.redirectUris()).as("the lost URI is not put back").doesNotContain(ACME_CALLBACK);
            assertThat(logs.lines(Level.ERROR))
                    .as("one ERROR that names the lost URI")
                    .hasSize(1)
                    .allSatisfy(line -> assertThat(line).contains(ACME_CALLBACK));
            assertThat(logs.all()).doesNotContain(ADMIN_SECRET);
        } finally {
            fixture.setWebClientUris(redirectBefore, logoutBefore);
        }
    }

    @Test
    @DisplayName(
            "AC-001.59 B1 the undo re-reads tsolve-web after its URI write, logs an ERROR naming a lost URI and still deletes the IdP and the Organization")
    void undoDetectsLostUriAndCarriesOn_AC_001_59_B1() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        List<String> redirectBefore = fixture.redirectUris();
        List<String> logoutBefore = fixture.logoutUris();

        KeycloakAdminClient client = spy(fixture.newAdminClient());
        AtomicInteger writes = new AtomicInteger();
        // write 1 (provision) is applied and then fails, so the undo has something to write; write 2 is the undo's
        doAnswer(invocation -> {
                    int number = writes.incrementAndGet();
                    Object result = invocation.callRealMethod();
                    if (number == 1) {
                        throw new IllegalStateException("simulated failure after the URI write");
                    }
                    if (number == 2) {
                        dropAcmeCallback();
                    }
                    return result;
                })
                .when(client)
                .writeWebClientUris(any());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            assertThatThrownBy(() -> provisioner.provision(workspaceId, slug, domain))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("simulated failure after the URI write");

            assertThat(writes.get()).as("the undo wrote the URI list").isEqualTo(2);
            assertThat(fixture.identityProvider("google-" + slug)).as("the undo went on to the IdP").isEmpty();
            assertThat(fixture.organizationsWithDomain(domain))
                    .as("the undo went on to the Organization")
                    .isEmpty();
            assertThat(fixture.redirectUris()).doesNotContain(callbackUri(slug), ACME_CALLBACK);
            assertThat(logs.lines(Level.ERROR))
                    .as("an ERROR names the lost URI")
                    .anySatisfy(line -> assertThat(line).contains(ACME_CALLBACK));
            assertThat(logs.all()).doesNotContain(ADMIN_SECRET);
        } finally {
            fixture.setWebClientUris(redirectBefore, logoutBefore);
        }
    }

    private static long mutatingCalls(KeycloakAdminClient client) {
        return mockingDetails(client).getInvocations().stream()
                .map(i -> i.getMethod().getName())
                .filter(name -> {
                    for (String prefix : MUTATING) {
                        if (name.startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                })
                .count();
    }
}
