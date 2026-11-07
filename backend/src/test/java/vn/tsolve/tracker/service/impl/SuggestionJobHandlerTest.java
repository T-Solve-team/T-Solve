package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.tsolve.tracker.exception.SuggestionJobFailedException;
import vn.tsolve.tracker.service.SuggestionJobService;

@ExtendWith(MockitoExtension.class)
class SuggestionJobHandlerTest {

    private static final UUID JOB_REF = UUID.fromString("7f3a2b1c-0000-4000-8000-000000000001");

    @Mock
    private SuggestionJobService jobService;

    @Test
    void runsTheSuggestionJobOfTheReferencedResult_AC_011_3() {
        SuggestionJobHandler handler = new SuggestionJobHandler(jobService);

        handler.execute(JOB_REF);

        assertThat(handler.handlerType()).isEqualTo(SuggestionJobHandler.TYPE);
        verify(jobService).run(JOB_REF);
    }

    @Test
    void letsAFailedJobFailSoTheWorkerRetriesIt_AC_011_6() {
        willThrow(new SuggestionJobFailedException("ITSUP-1", "IllegalStateException"))
                .given(jobService)
                .run(JOB_REF);

        assertThatThrownBy(() -> new SuggestionJobHandler(jobService).execute(JOB_REF))
                .isInstanceOf(SuggestionJobFailedException.class);
    }
}
