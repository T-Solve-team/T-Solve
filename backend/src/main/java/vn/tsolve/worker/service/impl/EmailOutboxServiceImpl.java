package vn.tsolve.worker.service.impl;

import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.util.EmailAddressRules;

public class EmailOutboxServiceImpl implements EmailOutboxService {
    private final EmailOutboxRepository repository;
    private final WorkerTenantService tenants;
    private final Clock clock;

    public EmailOutboxServiceImpl(EmailOutboxRepository repository, WorkerTenantService tenants, Clock clock) {
        this.repository = repository;
        this.tenants = tenants;
        this.clock = clock;
    }

    @Override
    public UUID queue(OutboundEmail outbound) {
        OutboundEmail email = EmailAddressRules.validate(outbound);
        UUID workspace = tenants.currentWorkspaceId().orElseThrow(InvalidOutboundEmailException::new);
        UUID id = UUID.randomUUID();
        try {
            repository.insert(id, workspace, email, clock.instant());
        } catch (DataAccessException ignored) {
            throw new EmailOutboxUnavailableException();
        }
        return id;
    }
}
