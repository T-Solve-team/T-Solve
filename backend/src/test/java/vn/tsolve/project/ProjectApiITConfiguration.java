package vn.tsolve.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        private final Map<String, TrackerProjectLookup> stubs = new HashMap<>();
        private final List<String> requested = new ArrayList<>();

        public void stub(String key, TrackerProjectLookup lookup) {
            stubs.put(key, lookup);
        }

        public void reset() {
            stubs.clear();
            requested.clear();
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
            return stubs.getOrDefault(key, TrackerProjectLookup.failed(Outcome.NOT_FOUND));
        }
    }
}
