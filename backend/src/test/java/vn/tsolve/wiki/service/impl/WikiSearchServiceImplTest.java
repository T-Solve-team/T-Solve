package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.wiki.repository.WikiSearchRepository;

class WikiSearchServiceImplTest {
    private final WikiSearchRepository repository = Mockito.mock(WikiSearchRepository.class);
    private final EmbeddingClient embeddings = Mockito.mock(EmbeddingClient.class);
    private final UUID user = UUID.randomUUID();
    private WikiSearchServiceImpl service;

    @BeforeEach
    void prepare() {
        service = new WikiSearchServiceImpl(repository, embeddings, 0.55);
    }

    @Test
    void embedsQueryAndUsesConfiguredThreshold_AC_002_7() {
        float[] vector = new float[EmbeddingClient.DIMENSIONS];
        vector[0] = 1;
        when(embeddings.embed(List.of("guide"))).thenReturn(List.of(vector));
        when(repository.search(eq(user), eq("guide"), any(), eq(0.55))).thenReturn(List.of());
        assertThat(service.search(user, " guide ")).isEmpty();
        verify(repository).search(eq(user), eq("guide"), eq(java.util.Arrays.toString(vector)), eq(0.55));
    }

    @Test
    void fallsBackToKeywordsWhenEmbeddingThrows_AC_002_8() {
        when(embeddings.embed(List.of("guide"))).thenThrow(new IllegalStateException("offline"));
        when(repository.search(user, "guide", null, 0.55)).thenReturn(List.of());
        assertThat(service.search(user, "guide")).isEmpty();
        verify(repository).search(user, "guide", null, 0.55);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\u2003", "\t\n"})
    void rejectsMissingOrBlankQuery_AC_002_9(String query) {
        assertThatThrownBy(() -> service.search(user, query))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG01");
                    assertThat(error.getField()).isEqualTo("q");
                    assertThat(error.getParams()).containsEntry("field_name", "q");
                });
        verifyNoInteractions(embeddings, repository);
    }

    @Test
    void checksLengthBeforeStripping_AC_002_9() {
        assertThatThrownBy(() -> service.search(user, " " + "a".repeat(199) + " "))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG02");
                    assertThat(error.getField()).isEqualTo("q");
                    assertThat(error.getParams()).containsEntry("max_length", 200);
                });
        verifyNoInteractions(embeddings, repository);
    }

    @Test
    void rejectsNulInQueryAsBadRequest_BUG_290() {
        assertThatThrownBy(() -> service.search(user, "repair\u0000guide"))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG06");
                    assertThat(error.getField()).isEqualTo("q");
                    assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(embeddings, repository);
    }

    @Test
    void acceptsTwoHundredCharactersAndSqlLikeText_AC_002_7_AC_002_9() {
        String query = "a".repeat(200);
        when(embeddings.embed(any())).thenThrow(new IllegalStateException("offline"));
        service.search(user, query);
        service.search(user, "'); DROP TABLE wiki_document; --");
        verify(repository).search(user, query, null, 0.55);
        verify(repository).search(user, "'); DROP TABLE wiki_document; --", null, 0.55);
    }
}
