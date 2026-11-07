package vn.tsolve.search.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import vn.tsolve.search.scope.entity.SearchTicket;

class EmbeddingTextTest {
    @Test
    void joinsPresentSectionsOnePerLine_AC_005_4() {
        SearchTicket ticket = mock(SearchTicket.class);
        when(ticket.getTitle()).thenReturn("Title");
        when(ticket.getSymptoms()).thenReturn("Symptoms");
        when(ticket.getRootCause()).thenReturn("Root cause");
        when(ticket.getResolutionSteps()).thenReturn("Resolution steps");
        when(ticket.getWorkaround()).thenReturn("Workaround");
        when(ticket.getScope()).thenReturn("Excluded scope");
        assertThat(EmbeddingText.of(ticket)).isEqualTo("Title\nSymptoms\nRoot cause\nResolution steps\nWorkaround");

        when(ticket.getSymptoms()).thenReturn(null);
        when(ticket.getRootCause()).thenReturn("  ");
        when(ticket.getWorkaround()).thenReturn("");
        assertThat(EmbeddingText.of(ticket)).isEqualTo("Title\nResolution steps");
    }
}
