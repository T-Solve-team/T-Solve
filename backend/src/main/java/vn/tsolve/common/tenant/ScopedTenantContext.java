package vn.tsolve.common.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

public final class ScopedTenantContext implements TenantContext {
    private static final Logger log = LoggerFactory.getLogger(ScopedTenantContext.class);
    private final ThreadLocal<Long> binding = new ThreadLocal<>();
    private final LongPredicate exists;
    private final TransactionOperations transactions;

    public ScopedTenantContext(LongPredicate exists, TransactionOperations transactions) {
        this.exists = Objects.requireNonNull(exists);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public Optional<Long> currentWorkspaceId() {
        return Optional.ofNullable(binding.get());
    }

    @Override
    public Long requireWorkspaceId() {
        return currentWorkspaceId().orElseThrow(() -> {
            var failure = new TenantAccessException();
            log.error("SQL tenant access requires a Workspace binding", failure);
            return failure;
        });
    }

    @Override
    public <T> T runIn(Long workspaceId, Supplier<T> action) {
        return withWorkspace(workspaceId, () -> transactions.execute(status -> action.get()));
    }

    /** Infrastructure entry point for the resolver; web requests do not open a transaction around the filter chain. */
    public <T> T withWorkspace(Long workspaceId, Supplier<T> action) {
        Long previous = binding.get();
        if (workspaceId == null || workspaceId <= 0 || (previous != null && !previous.equals(workspaceId))) {
            throw new TenantAccessException();
        }
        if (!exists.test(workspaceId)) {
            throw new TenantAccessException();
        }
        binding.set(workspaceId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                binding.remove();
            } else {
                binding.set(previous);
            }
        }
    }
}
