package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.review.repository.ReviewExpiryRepository;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class ReviewExpiryServiceImplTest {
    private final ReviewExpiryRepository repository = mock(ReviewExpiryRepository.class);
    private final LocalDate today = LocalDate.of(2026, 11, 9);

    @Test
    void groupsReturnedTicketsByProject_AC_014_5() {
        UUID workspace = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var tenants = new MockWorkerTenantService(Set.of(workspace), Set.of());
        var beans = new StaticListableBeanFactory(Map.of("tenants", tenants));
        var service = new ReviewExpiryServiceImpl(repository, beans.getBeanProvider(WorkerTenantService.class));
        when(repository.expireOverdue(today)).thenReturn(List.of(first, second, first));
        tenants.runIn(workspace, () -> assertThat(service.expireOverdue(today))
                .containsExactlyInAnyOrderEntriesOf(Map.of(first, 2, second, 1)));
    }

    @Test
    void noOverdueTicketsReturnsEmptyWithoutAWorkerBean_AC_014_5() {
        var beans = new StaticListableBeanFactory();
        var service = new ReviewExpiryServiceImpl(repository, beans.getBeanProvider(WorkerTenantService.class));
        when(repository.expireOverdue(today)).thenReturn(List.of());
        assertThat(service.expireOverdue(today)).isEmpty();
    }

    @Test
    void reportsCountsOnlyAfterCommitWithWorkspaceCaptured_AC_014_5_AC_014_6() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        var tenants = new MockWorkerTenantService(Set.of(workspace), Set.of());
        var beans = new StaticListableBeanFactory(Map.of("tenants", tenants));
        var service = new ReviewExpiryServiceImpl(repository, beans.getBeanProvider(WorkerTenantService.class));
        when(repository.expireOverdue(today)).thenReturn(List.of(project, project));
        Logger logger = (Logger) LoggerFactory.getLogger(ReviewExpiryServiceImpl.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        TransactionSynchronizationManager.initSynchronization();
        try {
            tenants.runIn(workspace, () -> service.expireOverdue(today));
            assertThat(events.list).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(events.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                    .isEqualTo("JOB-02 expired 2 tickets in Project " + project + " of Workspace " + workspace));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(events);
            events.stop();
        }
    }
}
