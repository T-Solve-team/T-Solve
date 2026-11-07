package vn.tsolve.worker.config;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

@Configuration(proxyBeanMethods = false)
@Profile("worker-mock")
public class WorkerMockConfiguration {
    @Bean
    MockWorkerTenantService mockWorkerTenantService(
            @Value("${tsolve.worker.mock.active-workspaces:}") String active,
            @Value("${tsolve.worker.mock.suspended-workspaces:}") String suspended) {
        return new MockWorkerTenantService(workspaces(active), workspaces(suspended));
    }

    private static Set<UUID> workspaces(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }
}
