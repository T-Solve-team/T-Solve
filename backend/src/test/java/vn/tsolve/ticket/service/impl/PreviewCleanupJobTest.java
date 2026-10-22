package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.ticket.repository.ImportPreviewRepository;

class PreviewCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-22T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ImportPreviewRepository previews = mock(ImportPreviewRepository.class);
    private final PreviewCleanupJob job = new PreviewCleanupJob(previews, CLOCK);

    @Test
    void deletesEveryPreviewOlderThanTwentyFourHours_AC_006_1() {
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of(3L, 5L));

        int deleted = job.deleteExpiredPreviews();

        assertThat(deleted).isEqualTo(2);
        verify(previews).deleteById(3L);
        verify(previews).deleteById(5L);
    }

    @Test
    void keepsPreviewsYoungerThanTwentyFourHours_AC_006_2() {
        // the cut-off is exactly now minus 24 h and the query is "created before" it, so a younger preview is kept
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of());

        assertThat(job.deleteExpiredPreviews()).isZero();
        verify(previews).findIdsCreatedBefore(NOW.minusSeconds(24 * 3600));
    }

    @Test
    void keepsGoingWhenOneDeletionFails_AC_006_3() {
        when(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).thenReturn(List.of(3L, 4L, 5L));
        doThrow(new DataAccessResourceFailureException("lock timeout"))
                .when(previews)
                .deleteById(4L);

        int deleted = job.deleteExpiredPreviews();

        assertThat(deleted).isEqualTo(2);
        verify(previews).deleteById(3L);
        verify(previews).deleteById(5L);
    }
}
