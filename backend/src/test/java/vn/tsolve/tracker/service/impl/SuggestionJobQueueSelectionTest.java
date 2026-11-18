package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.tracker.service.SuggestionJobService;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WebJobQueue;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Which queue the app wires: the worker's when it is enabled, the web one that queues for the worker process when web
 * jobs are enabled (AC-011.10), the in-process one otherwise (AC-011.9).
 */
class SuggestionJobQueueSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(SuggestionJobService.class, () -> mock(SuggestionJobService.class))
            .withBean(TenantJobService.class, () -> mock(TenantJobService.class))
            .withBean(WorkerTenantService.class, () -> mock(WorkerTenantService.class))
            .withBean(WebJobQueue.class, () -> mock(WebJobQueue.class))
            .withUserConfiguration(
                    InProcessSuggestionJobQueue.class, WorkerSuggestionJobQueue.class, WebSuggestionJobQueue.class);

    @Test
    void runsInProcessWhenTheWorkerIsNotConfigured_AC_011_9() {
        runner.run(context ->
                assertThat(context.getBean(SuggestionJobQueue.class)).isInstanceOf(InProcessSuggestionJobQueue.class));
    }

    @Test
    void runsInProcessWhenTheWorkerIsDisabled_AC_011_9() {
        runner.withPropertyValues("tsolve.worker.enabled=false")
                .run(context -> assertThat(context.getBean(SuggestionJobQueue.class))
                        .isInstanceOf(InProcessSuggestionJobQueue.class));
    }

    @Test
    void usesTheWorkerWhenItIsEnabled_AC_011_1() {
        runner.withPropertyValues("tsolve.worker.enabled=true")
                .run(context -> assertThat(context.getBean(SuggestionJobQueue.class))
                        .isInstanceOf(WorkerSuggestionJobQueue.class));
    }

    @Test
    void queuesForTheWorkerProcessWhenWebJobsAreEnabled_AC_011_10_BUG_342() {
        runner.withPropertyValues("tsolve.worker.web-jobs-enabled=true")
                .run(context -> assertThat(context.getBean(SuggestionJobQueue.class))
                        .isInstanceOf(WebSuggestionJobQueue.class));
    }

    @Test
    void usesTheWorkerWhenBothAreEnabled_AC_011_1_BUG_342() {
        runner.withPropertyValues("tsolve.worker.web-jobs-enabled=true", "tsolve.worker.enabled=true")
                .run(context -> assertThat(context.getBean(SuggestionJobQueue.class))
                        .isInstanceOf(WorkerSuggestionJobQueue.class));
    }
}
