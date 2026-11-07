package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WorkerSuggestionJobQueueTest {

    private static final UUID JOB_REF = UUID.fromString("7f3a2b1c-0000-4000-8000-000000000001");
    private static final UUID BOUND = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000001");
    private static final UUID DEV = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000001");
    private static final SuggestionJobRequest REQUEST = new SuggestionJobRequest(JOB_REF, "ITSUP-1");

    @Mock
    private TenantJobService jobs;

    @Mock
    private WorkerTenantService tenants;

    @Test
    void enqueuesOnlyTheReferenceInTheBoundWorkspace_AC_011_1() {
        given(tenants.currentWorkspaceId()).willReturn(Optional.of(BOUND));

        queue(DEV.toString()).enqueue(REQUEST);

        verify(jobs).enqueue(SuggestionJobHandler.TYPE, JOB_REF);
        verify(tenants, never()).runIn(any(), any());
    }

    @Test
    void bindsTheConfiguredDevWorkspaceWhenNoneIsBound_AC_011_1() {
        given(tenants.currentWorkspaceId()).willReturn(Optional.empty());
        willAnswer(call -> {
                    call.<Runnable>getArgument(1).run();
                    return null;
                })
                .given(tenants)
                .runIn(eq(DEV), any());

        queue(DEV.toString()).enqueue(REQUEST);

        verify(tenants).runIn(eq(DEV), any());
        verify(jobs).enqueue(SuggestionJobHandler.TYPE, JOB_REF);
    }

    @Test
    void logsWithoutThrowingWhenNoWorkspaceIsBoundOrConfigured_AC_011_1(CapturedOutput output) {
        given(tenants.currentWorkspaceId()).willReturn(Optional.empty());

        assertThatCode(() -> queue("").enqueue(REQUEST)).doesNotThrowAnyException();

        verifyNoInteractions(jobs);
        assertThat(output).contains("ITSUP-1");
    }

    @Test
    void logsWithoutThrowingWhenTheWorkerRefusesTheJob_AC_011_1(CapturedOutput output) {
        given(tenants.currentWorkspaceId()).willReturn(Optional.of(BOUND));
        given(jobs.enqueue(SuggestionJobHandler.TYPE, JOB_REF))
                .willThrow(new IllegalStateException("Jobs database unavailable"));

        assertThatCode(() -> queue(DEV.toString()).enqueue(REQUEST)).doesNotThrowAnyException();

        assertThat(output).contains("ITSUP-1").contains("IllegalStateException");
    }

    private WorkerSuggestionJobQueue queue(String devWorkspaceId) {
        return new WorkerSuggestionJobQueue(jobs, tenants, devWorkspaceId);
    }
}
