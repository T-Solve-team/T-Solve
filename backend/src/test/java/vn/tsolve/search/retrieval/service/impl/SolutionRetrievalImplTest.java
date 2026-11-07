package vn.tsolve.search.retrieval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;
import vn.tsolve.search.retrieval.repository.SolutionRetrievalRepository;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

class SolutionRetrievalImplTest {
    private final SolutionRetrievalRepository repository = mock(SolutionRetrievalRepository.class);
    private final SolutionRetrieval retrieval = new SolutionRetrievalImpl(repository);

    @Test
    void rejectsBadArguments_AC_010_5() {
        for (float[] vector : new float[][] {null, new float[0], new float[1023], new float[1025]}) {
            assertThatIllegalArgumentException().isThrownBy(() -> retrieval.nearest(vector, 0.7, 5));
        }
        for (double minimum : new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> retrieval.nearest(vector(), minimum, 5));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> retrieval.nearest(vector(), 0.7, 0));
        float[] nonFinite = vector();
        nonFinite[1] = Float.NaN;
        assertThatIllegalArgumentException().isThrownBy(() -> retrieval.nearest(nonFinite, 0.7, 5));
        verifyNoInteractions(repository);
    }

    @Test
    void returnsSectionsWithoutTicketDetails_AC_010_2() {
        var row = mock(SolutionRetrievalRepository.SolutionRow.class);
        when(row.getSymptoms()).thenReturn("symptoms");
        when(row.getRootCause()).thenReturn("cause");
        when(row.getResolutionSteps()).thenReturn("steps");
        when(row.getWorkaround()).thenReturn("  \n ");
        when(row.getScopeOfApplicability()).thenReturn("scope");
        when(row.getSimilarity()).thenReturn(0.9);
        when(repository.nearest(anyString(), anyDouble(), anyInt())).thenReturn(List.of(row));

        assertThat(retrieval.nearest(vector(), 0.7, 5))
                .containsExactly(new RetrievedSolution("symptoms", "cause", "steps", null, "scope", 0.9));
        when(row.getWorkaround()).thenReturn("temporary fix");
        assertThat(retrieval.nearest(vector(), 0.7, 5).getFirst().workaround()).isEqualTo("temporary fix");
        assertThat(Arrays.stream(RetrievedSolution.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly(
                        "symptoms", "rootCause", "resolutionSteps", "workaround", "scopeOfApplicability", "similarity");
    }

    @Test
    void takesNoUserAndEmbedsNothing_AC_010_4() throws Exception {
        when(repository.nearest(anyString(), anyDouble(), anyInt())).thenReturn(List.of());
        assertThat(retrieval.nearest(vector(), 0, 1)).isEmpty();
        verify(repository).nearest(PgVector.literal(vector()), 0, 1);
        assertThat(SolutionRetrieval.class.getMethod("nearest", float[].class, double.class, int.class))
                .isNotNull();
        assertThat(SolutionRetrievalImpl.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType()).isEqualTo(SolutionRetrievalRepository.class));
    }

    @Test
    void rejectsBadDepartmentArguments_AC_011_4() {
        for (float[] vector : new float[][] {null, new float[0], new float[1023], new float[1025]}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> retrieval.nearestInProjectDepartment(1, vector, 0.7, 5));
        }
        for (double minimum : new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> retrieval.nearestInProjectDepartment(1, vector(), minimum, 5));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> retrieval.nearestInProjectDepartment(1, vector(), 0.7, 0));
        float[] nonFinite = vector();
        nonFinite[1] = Float.NaN;
        assertThatIllegalArgumentException()
                .isThrownBy(() -> retrieval.nearestInProjectDepartment(1, nonFinite, 0.7, 5));
        verifyNoInteractions(repository);
    }

    @Test
    void returnsTicketIdAndSections_AC_011_2() {
        UUID ticketId = UUID.randomUUID();
        var row = mock(SolutionRetrievalRepository.TicketSolutionRow.class);
        when(row.getTicketId()).thenReturn(ticketId);
        when(row.getSymptoms()).thenReturn("symptoms");
        when(row.getRootCause()).thenReturn("cause");
        when(row.getResolutionSteps()).thenReturn("steps");
        when(row.getWorkaround()).thenReturn(" ");
        when(row.getScopeOfApplicability()).thenReturn("scope");
        when(row.getSimilarity()).thenReturn(0.8);
        when(repository.nearestInProjectDepartment(anyLong(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(row));

        assertThat(retrieval.nearestInProjectDepartment(7, vector(), 0.7, 5))
                .containsExactly(
                        new RetrievedTicketSolution(ticketId, "symptoms", "cause", "steps", null, "scope", 0.8));
        when(row.getWorkaround()).thenReturn("temporary fix");
        assertThat(retrieval
                        .nearestInProjectDepartment(7, vector(), 0.7, 5)
                        .getFirst()
                        .workaround())
                .isEqualTo("temporary fix");
        assertThat(Arrays.stream(RetrievedTicketSolution.class.getRecordComponents())
                        .map(component -> component.getName()))
                .containsExactly(
                        "ticketId",
                        "symptoms",
                        "rootCause",
                        "resolutionSteps",
                        "workaround",
                        "scopeOfApplicability",
                        "similarity");
    }

    @Test
    void takesNoUserAndEmbedsNothing_AC_011_5() throws Exception {
        when(repository.nearestInProjectDepartment(anyLong(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());
        assertThat(retrieval.nearestInProjectDepartment(7, vector(), 0, 1)).isEmpty();
        verify(repository).nearestInProjectDepartment(7, PgVector.literal(vector()), 0, 1);
        assertThat(SolutionRetrieval.class.getMethod(
                        "nearestInProjectDepartment", long.class, float[].class, double.class, int.class))
                .isNotNull();
        assertThat(SolutionRetrieval.class.getDeclaredMethods()).hasSize(2);
        assertThat(SolutionRetrievalImpl.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType()).isEqualTo(SolutionRetrievalRepository.class));
    }

    private static float[] vector() {
        float[] vector = new float[1024];
        vector[0] = 1;
        return vector;
    }
}
