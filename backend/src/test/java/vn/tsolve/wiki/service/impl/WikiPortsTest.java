package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.triage.dto.WikiSource;
import vn.tsolve.wiki.repository.AccessRepository;

class WikiPortsTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final TSolveUser USER = new TSolveUser(USER_ID, "viewer@example.test", "Viewer", Set.of());
    private final AccessRepository repository = mock(AccessRepository.class);

    @Test
    void mapsRankedPassagesAndForwardsThresholdAndLimit_AC_001_5() {
        float[] vector = new float[1024];
        vector[0] = 1;
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var rows = List.of(passage(first, "First", "Best passage", 1), passage(second, "Second", "Next passage", 0.8));
        when(repository.nearestVisible(USER_ID, Arrays.toString(vector), 0.7, 2))
                .thenReturn(rows);

        assertThat(new WikiSourcesImpl(repository).nearest(USER, vector, 0.7, 2))
                .containsExactly(
                        new WikiPassage(first, "First", "Best passage", 1),
                        new WikiPassage(second, "Second", "Next passage", 0.8));
        verify(repository).nearestVisible(USER_ID, Arrays.toString(vector), 0.7, 2);
    }

    @Test
    void mapsTheProjectDepartmentPassagesAndForwardsTheProject_AC_001_12() {
        float[] vector = new float[1024];
        vector[0] = 1;
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        var row = passage(document, "Runbook", "Restart the service", 0.85);
        when(repository.nearestInProjectDepartment(project, Arrays.toString(vector), 0.7, 3))
                .thenReturn(List.of(row));

        assertThat(new WikiSourcesImpl(repository).nearestInProjectDepartment(project, vector, 0.7, 3))
                .containsExactly(new WikiPassage(document, "Runbook", "Restart the service", 0.85));
        verify(repository).nearestInProjectDepartment(project, Arrays.toString(vector), 0.7, 3);
    }

    @Test
    void mapsCompanyWidePassagesAndForwardsThresholdAndLimit_AC_001_7() {
        float[] vector = new float[1024];
        vector[0] = 1;
        UUID document = UUID.randomUUID();
        var row = passage(document, "Guide", "Passage", 0.9);
        when(repository.nearestCompanyWide(Arrays.toString(vector), 0.7, 1)).thenReturn(List.of(row));

        assertThat(new WikiSourceRetrievalImpl(repository).nearest(vector, 0.7, 1))
                .containsExactly(new WikiSource(document.toString(), "Guide", "Passage", 0.9));
        verify(repository).nearestCompanyWide(Arrays.toString(vector), 0.7, 1);
    }

    @Test
    void mapsPublicSpaceIdsInRepositoryOrder_AC_001_9_AC_001_10() {
        var alpha = mock(AccessRepository.SpaceRow.class);
        var zulu = mock(AccessRepository.SpaceRow.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(alpha.getSpaceId()).thenReturn(first);
        when(alpha.getName()).thenReturn("Alpha");
        when(zulu.getSpaceId()).thenReturn(second);
        when(zulu.getName()).thenReturn("Zulu");
        when(repository.findManagedSpaces(USER_ID)).thenReturn(List.of(alpha));
        when(repository.findViewableSpaces(USER_ID)).thenReturn(List.of(alpha, zulu));

        var lookups = new WikiSpaceLookups(repository);
        assertThat(lookups.findManagedSpaces(USER_ID)).containsExactly(new SpaceRef(first, "Alpha"));
        assertThat(lookups.findViewableSpaces(USER_ID))
                .containsExactly(new SpaceRef(first, "Alpha"), new SpaceRef(second, "Zulu"));
    }

    @Test
    void reportsExactlyTheUnavailableInputIds_AC_001_6() {
        UUID available = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(repository.findAvailableDocuments(eq(USER_ID), anyCollection())).thenReturn(Set.of(available));

        assertThat(new WikiSourcesImpl(repository).unavailable(USER, Set.of(available, missing)))
                .containsExactly(missing);
    }

    @Test
    void skipsAnEmptyCitationSet_AC_001_6() {
        assertThat(new WikiSourcesImpl(repository).unavailable(USER, Set.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void dropsMalformedIdsAndPreservesTheGivenAvailableIds_AC_001_8() {
        String available = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        String missing = UUID.randomUUID().toString();
        when(repository.findAvailableDocuments(eq(USER_ID), anyCollection()))
                .thenReturn(Set.of(UUID.fromString(available)));

        assertThat(new WikiDocumentAccessImpl(repository)
                        .available(USER_ID.toString(), List.of(available, missing, "old-id", "1-1-1-1-1")))
                .containsExactly(available);
    }

    @Test
    void failsClosedForAnInvalidUserOrNoValidDocumentIds_AC_001_8() {
        var access = new WikiDocumentAccessImpl(repository);
        assertThat(access.available("legacy-user", List.of(UUID.randomUUID().toString())))
                .isEmpty();
        assertThat(access.available(USER_ID.toString(), List.of("legacy-id"))).isEmpty();
        assertThat(access.available(USER_ID.toString(), List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static AccessRepository.PassageRow passage(UUID id, String title, String text, double similarity) {
        var row = mock(AccessRepository.PassageRow.class);
        when(row.getDocumentId()).thenReturn(id);
        when(row.getTitle()).thenReturn(title);
        when(row.getText()).thenReturn(text);
        when(row.getSimilarity()).thenReturn(similarity);
        return row;
    }
}
