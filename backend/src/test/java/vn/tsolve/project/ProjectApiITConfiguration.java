package vn.tsolve.project;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.service.TrackerProjectService;

/** Shares one context for all Project ITs; the Jira double asserts no active transaction (AC-002.18, D43). */
@TestConfiguration(proxyBeanMethods = false)
@Import(TwoTenantDatabases.class)
public class ProjectApiITConfiguration {
    @Bean
    @Primary
    FakeTrackerProjectService fakeTrackerProjectService() {
        return new FakeTrackerProjectService();
    }

    public static class FakeTrackerProjectService implements TrackerProjectService {
        private static final Runnable NOTHING = () -> {};

        private final Map<String, TrackerProjectLookup> stubs = new ConcurrentHashMap<>();
        private final List<String> requested = new CopyOnWriteArrayList<>();
        private volatile Runnable hook = NOTHING;

        public void stub(String key, TrackerProjectLookup lookup) {
            stubs.put(key, lookup);
        }

        /** Runs during the Jira read, outside any transaction: a race barrier or a concurrent change. */
        public void beforeAnswer(Runnable hook) {
            this.hook = hook;
        }

        public void reset() {
            stubs.clear();
            requested.clear();
            hook = NOTHING;
        }

        public List<String> calls() {
            return List.copyOf(requested);
        }

        @Override
        public TrackerProjectLookup findProject(String key) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new AssertionError("Jira called inside a transaction");
            }
            requested.add(key);
            hook.run();
            return stubs.getOrDefault(key, TrackerProjectLookup.failed(Outcome.NOT_FOUND));
        }
    }
}
