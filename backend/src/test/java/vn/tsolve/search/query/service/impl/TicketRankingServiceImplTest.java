package vn.tsolve.search.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.mapper.TicketSearchResultMapper;
import vn.tsolve.search.query.repository.TicketRankingRepository;
import vn.tsolve.search.scope.service.VisibleScope.Scope;

class TicketRankingServiceImplTest {
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final TicketRankingRepository repository = mock(TicketRankingRepository.class);
    private final TicketSearchResultMapper mapper = mock(TicketSearchResultMapper.class);
    private final TicketRankingServiceImpl service = new TicketRankingServiceImpl(embeddings, repository, mapper, 0.5);
    private final PageRequest paging = PageRequest.of(0, 20);
    private final RankFilter filter = new RankFilter(null, null, null, null);
    private final Scope scope = new Scope(Set.of(7L), Set.of(), Optional.empty());

    @Test
    void fallsBackToKeywordWhenOllamaFails_AC_007_2() {
        when(embeddings.embed(List.of("query text"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            throw new AiUnavailableException();
        });
        when(repository.keyword(eq(Set.of(7L)), eq(null), eq(null), eq(null), eq(null), any(), eq(paging)))
                .thenReturn(Page.empty(paging));

        var result = service.rank("query text", "50%_off\\", filter, scope, paging);

        assertThat(result.isFallback()).isTrue();
        assertThat(result.page()).isEmpty();
        verify(repository).keyword(Set.of(7L), null, null, null, null, "%50\\%\\_off\\\\%", paging);
        verifyNoInteractions(mapper);
    }

    @Test
    void skipsEmbeddingForEmptyScope_AC_006_2() {
        var result = service.rank("query", "query", filter, Scope.empty(), paging);
        assertThat(result.page()).isEmpty();
        assertThat(result.isFallback()).isFalse();
        service.rank(
                "query",
                "query",
                filter,
                new Scope(Set.of(), Set.of(UUID.randomUUID()), Optional.of("writer")),
                paging);
        verifyNoInteractions(embeddings, repository, mapper);
    }

    @Test
    void sendsOnlyQueryAndUsesSemanticCutOff_AC_007_1_AC_007_9() {
        when(embeddings.embed(List.of("query"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return List.of(new float[] {1, 0});
        });
        when(repository.semantic(Set.of(7L), null, null, null, null, "[1.0,0.0]", 0.5, paging))
                .thenReturn(Page.empty(paging));
        var result = service.rank("query", "keyword", filter, scope, paging);
        assertThat(result.isFallback()).isFalse();
        assertThat(result.page()).isEmpty();
        verify(embeddings).embed(List.of("query"));
        verify(repository).semantic(Set.of(7L), null, null, null, null, "[1.0,0.0]", 0.5, paging);
    }
}
