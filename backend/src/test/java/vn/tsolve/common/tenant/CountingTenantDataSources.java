package vn.tsolve.common.tenant;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Test-only wrapper of the {@link TenantDataSources} port: it counts the calls and can send one Workspace to another's
 * pool (the injected misroute of Edge #5). It closes the wrapped pools, because Spring destroys the wrapper.
 */
final class CountingTenantDataSources implements TenantDataSources, AutoCloseable {

    private final TenantDataSources delegate;
    private final Map<UUID, UUID> misroutes;
    private final AtomicInteger calls = new AtomicInteger();

    private CountingTenantDataSources(TenantDataSources delegate, Map<UUID, UUID> misroutes) {
        this.delegate = delegate;
        this.misroutes = misroutes;
    }

    @Override
    public DataSource poolOf(UUID workspaceId) {
        calls.incrementAndGet();
        return delegate.poolOf(misroutes.getOrDefault(workspaceId, workspaceId));
    }

    int calls() {
        return calls.get();
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    /** Wraps the port bean of one context before the router receives it. */
    static final class Wrapping implements BeanPostProcessor {

        private final Map<UUID, UUID> misroutes;

        Wrapping(Map<UUID, UUID> misroutes) {
            this.misroutes = misroutes;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof TenantDataSources port && !(bean instanceof CountingTenantDataSources)) {
                return new CountingTenantDataSources(port, misroutes);
            }
            return bean;
        }
    }
}
