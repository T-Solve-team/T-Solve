package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobHandler;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class TenantJobServiceImplTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final MockWorkerTenantService context = new MockWorkerTenantService(Set.of(workspace), Set.of());
    private final InMemoryStorageProvider storage = new InMemoryStorageProvider();
    private final JobMapper mapper = new JobMapper(new JacksonJsonMapper());
    private final AtomicInteger calls = new AtomicInteger();
    private final TenantJobHandler businessHandler = new TenantJobHandler() {
        public String handlerType() {
            return "test-record";
        }

        public void execute(UUID id) {
            assertThat(context.currentWorkspaceId()).contains(workspace);
            assertThat(id).isEqualTo(recordId);
            calls.incrementAndGet();
        }
    };
    private final TenantJobRequestHandler handler =
            new TenantJobRequestHandler(new TenantExecutionServiceImpl(context), List.of(businessHandler));

    @AfterEach
    void closeStorage() {
        storage.close();
    }

    @Test
    void persistsCapturedWorkspaceAndRoundTripsPayload_AC_002_1_AC_002_3() {
        storage.setJobMapper(mapper);
        var queue = new TenantJobServiceImpl(context, new JobRequestScheduler(storage), handler);
        context.runIn(workspace, () -> {
            UUID jobId = queue.enqueue("test-record", recordId);
            var job = mapper.deserializeJob(mapper.serializeJob(storage.getJobById(jobId)));
            var request = (TenantJobRequest) job.getJobDetails().getJobParameterValues()[0];
            assertThat(request.workspaceId()).isEqualTo(workspace);
            assertThat(request.recordId()).isEqualTo(recordId);
            assertThat(request.handlerType()).isEqualTo("test-record");
            handler.run(request);
        });
        assertThat(calls).hasValue(1);
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void rejectsUnboundAndInvalidEnqueueWithoutPersisting_AC_002_2() {
        storage.setJobMapper(mapper);
        var queue = new TenantJobServiceImpl(context, new JobRequestScheduler(storage), handler);
        assertThatThrownBy(() -> queue.enqueue("test-record", recordId)).isInstanceOf(IllegalStateException.class);
        context.runIn(workspace, () -> {
            assertThatThrownBy(() -> queue.enqueue("unknown", recordId)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.enqueue("test-record", null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> queue.enqueue(null, recordId)).isInstanceOf(RuntimeException.class);
        });
        assertThat(storage.getJobStats().getTotal()).isZero();
    }

    @Test
    void rejectsMalformedPersistedJobs_AC_002_15() {
        for (TenantJobRequest request : List.of(
                new TenantJobRequest(null, "test-record", recordId),
                new TenantJobRequest(workspace, "unknown", recordId),
                new TenantJobRequest(workspace, "test-record", null))) {
            assertThatThrownBy(() -> handler.run(request)).isInstanceOf(RuntimeException.class);
        }
        assertThat(calls).hasValue(0);
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void rechecksWorkspaceStatusAndPreservesFailureForRetry_AC_002_10() {
        WorkerTenantService platform = mock(WorkerTenantService.class);
        when(platform.currentWorkspaceId()).thenAnswer(ignored -> context.currentWorkspaceId());
        when(platform.isWorkspaceActive(workspace)).thenReturn(true, false);
        org.mockito.Mockito.doAnswer(call -> {
                    context.runIn(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .when(platform)
                .runIn(org.mockito.ArgumentMatchers.eq(workspace), org.mockito.ArgumentMatchers.any());
        var original = new IllegalArgumentException("business failure");
        AtomicInteger attempts = new AtomicInteger();
        TenantJobHandler failing = new TenantJobHandler() {
            public String handlerType() {
                return "fails";
            }

            public void execute(UUID id) {
                attempts.incrementAndGet();
                throw original;
            }
        };
        var dispatcher = new TenantJobRequestHandler(new TenantExecutionServiceImpl(platform), List.of(failing));
        var request = new TenantJobRequest(workspace, "fails", recordId);
        assertThatThrownBy(() -> dispatcher.run(request)).isSameAs(original);
        assertThat(context.currentWorkspaceId()).isEmpty();
        assertThatThrownBy(() -> dispatcher.run(request)).isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void rejectsDuplicateHandlerTypes() {
        assertThatThrownBy(() -> new TenantJobRequestHandler(
                        new TenantExecutionServiceImpl(context), List.of(businessHandler, businessHandler)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
