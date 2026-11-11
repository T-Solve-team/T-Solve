package vn.tsolve.identity.jiralink;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The Jira link request in Redis (AC-001.69, AC-001.38, AC-001.72): a random single-use id kept for 10 minutes under
 * a hashed key, and the read-and-delete that uses it up. "Expired" is made by deleting the key (what Redis does at
 * expiry); the time to live itself is asserted in the first test. No sleeping.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "TSOLVE_SITE_ORIGIN_TEMPLATE=http://{slug}.localhost:8080")
class JiraLinkRequestServiceTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String TRACKER_ACCOUNT = "jira-acct-1";
    private static final String JIRA_USER = "Nguyen Van A";
    private static final String SITE = "acme-corp";
    private static final String KEY_PREFIX = "tsolve:jiralink:";
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(http://[a-z]+\\.localhost:8080)/jira-link\\?request=([A-Za-z0-9_-]{43})$");

    @Autowired
    private JiraLinkRequestService service;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    private static String idOf(String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        assertThat(matcher.matches()).as("link URL %s", url).isTrue();
        return matcher.group(2);
    }

    @Test
    @DisplayName("AC-001.69 stores a single-use request in Redis for 10 minutes and returns the Workspace site link")
    void storesTheRequestForTenMinutesAndReturnsTheWorkspaceSiteUrl(CapturedOutput output) {
        String url = service.create(ACME.id(), TRACKER_ACCOUNT, JIRA_USER, SITE);

        Matcher matcher = URL_PATTERN.matcher(url);
        assertThat(matcher.matches()).as("link URL %s", url).isTrue();
        assertThat(matcher.group(1)).isEqualTo("http://acme.localhost:8080");
        String id = matcher.group(2);
        assertThat(Base64.getUrlDecoder().decode(id)).hasSize(32);

        String key = KEY_PREFIX + sha256Hex(id);
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.hasKey(KEY_PREFIX + id))
                .as("the id itself is never part of a key")
                .isFalse();
        Map<Object, Object> stored = redis.opsForHash().entries(key);
        assertThat(stored).hasSize(4);
        assertThat(stored)
                .containsEntry("workspace_id", ACME.id().toString())
                .containsEntry("tracker_account_id", TRACKER_ACCOUNT);
        assertThat(stored.values()).contains(JIRA_USER, SITE);
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(590L).isLessThanOrEqualTo(600L);

        assertThat(idOf(service.create(BETA.id(), TRACKER_ACCOUNT, JIRA_USER, SITE)))
                .as("a request for beta uses beta's site origin")
                .isNotEqualTo(id);

        // edge 1 (guessing): ids do not repeat and none of them reaches a log line
        Set<String> ids = new HashSet<>();
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String next = idOf(service.create(ACME.id(), TRACKER_ACCOUNT, JIRA_USER, SITE));
            ids.add(next);
            all.add(next);
        }
        assertThat(ids).hasSize(200);
        assertThat(all).noneMatch(x -> output.getAll().contains(x));
        assertThat(output.getAll()).doesNotContain(id);
    }

    @Test
    @DisplayName("AC-001.69 edge 2 a request is usable once even under parallel confirms; AC-001.38 other Workspace")
    void aRequestIsUsableOnceEvenUnderParallelConfirms() throws Exception {
        String id = idOf(service.create(ACME.id(), TRACKER_ACCOUNT, JIRA_USER, SITE));
        JiraLinkRequest expected = new JiraLinkRequest(ACME.id(), TRACKER_ACCOUNT, JIRA_USER, SITE);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Optional<JiraLinkRequest>>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return service.consume(id, ACME.id());
                }));
            }
            go.countDown();
            List<Optional<JiraLinkRequest>> answers = new ArrayList<>();
            for (Future<Optional<JiraLinkRequest>> result : results) {
                answers.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(answers.stream().filter(Optional::isPresent).count()).isEqualTo(1);
            assertThat(answers.stream().filter(Optional::isEmpty).count()).isEqualTo(1);
            assertThat(answers.stream().flatMap(Optional::stream).findFirst()).contains(expected);
        } finally {
            pool.shutdownNow();
        }
        assertThat(service.consume(id, ACME.id())).isEmpty();
        assertThat(redis.hasKey(KEY_PREFIX + sha256Hex(id))).isFalse();

        // AC-001.38: a request made for acme read for beta gives nothing, and (AC-001.72) is used up
        String forAcme = idOf(service.create(ACME.id(), TRACKER_ACCOUNT, JIRA_USER, SITE));
        UUID other = BETA.id();
        assertThat(service.consume(forAcme, other)).isEmpty();
        assertThat(redis.hasKey(KEY_PREFIX + sha256Hex(forAcme))).isFalse();
        assertThat(service.consume(forAcme, ACME.id())).isEmpty();
    }
}
