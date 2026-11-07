package vn.tsolve.search.embedding.service.impl;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.tsolve.search.config.SearchSchedulingConfig;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;

class EmbeddingCatchUpJobTest {
    @Test
    void embedsLowestIdsWithoutVector_AC_005_7() {
        TicketEmbeddingRepository repository = mock(TicketEmbeddingRepository.class);
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        when(service.skippedIds()).thenReturn(Set.of(1L));
        when(repository.findPublishedWithoutEmbedding(eq(4), aryEq(new Long[] {1L})))
                .thenReturn(List.of(2L, 3L));

        EmbeddingCatchUpJob job = new EmbeddingCatchUpJob(repository, service, 4);
        job.run();

        verify(repository).findPublishedWithoutEmbedding(eq(4), aryEq(new Long[] {1L}));
        verify(service).embed(List.of(2L, 3L));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SearchSchedulingConfig.class);
            context.registerBean(EmbeddingCatchUpJob.class, () -> job);
            context.refresh();
        }
    }
}
