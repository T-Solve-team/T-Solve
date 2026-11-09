package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Test;
import vn.tsolve.review.dto.ReviewExpiryJobRequest;
import vn.tsolve.worker.config.JobRunrRuntime;

class ReviewExpiryRegistrationTest {
    @Test
    void repeatedRegistrationKeepsOneMidnightVietnamJob_AC_014_1() {
        try (var storage = new InMemoryStorageProvider()) {
            storage.setJobMapper(new JobMapper(new JacksonJsonMapper()));
            var scheduler = new JobRequestScheduler(storage);
            var runtime = mock(JobRunrRuntime.class);
            when(runtime.scheduler()).thenReturn(scheduler);
            var registration = new ReviewExpiryRegistration(runtime);
            registration.register();
            registration.register();
            assertThat(storage.getRecurringJobs()).singleElement().satisfies(job -> {
                assertThat(job.getId()).isEqualTo("review-job-02-expire-overdue");
                assertThat(job.getScheduleExpression()).isEqualTo("0 0 * * *");
                assertThat(job.getZoneId()).isEqualTo("Asia/Ho_Chi_Minh");
            });
        }
    }

    @Test
    void jobRunrHonorsZeroRetriesOnTheRequestHandler_AC_014_6() {
        try (var storage = new InMemoryStorageProvider()) {
            storage.setJobMapper(new JobMapper(new JacksonJsonMapper()));
            var scheduler = new JobRequestScheduler(storage);
            var id = scheduler.enqueue(new ReviewExpiryJobRequest(UUID.randomUUID(), 1));
            assertThat(storage.getJobById(id.asUUID()).getAmountOfRetries()).isZero();
        }
    }
}
