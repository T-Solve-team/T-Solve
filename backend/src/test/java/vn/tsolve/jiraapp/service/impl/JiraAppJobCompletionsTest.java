package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JiraAppJobCompletionsTest {

    @Test
    void keepsTheApplicationExecutorAndClosesTheBoundedDaemonPool_AC_007_5_AC_010_17() {
        var pool = new java.util.concurrent.atomic.AtomicReference<ThreadPoolExecutor>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withBean("redisAskJobStore", RedisAskJobStore.class, () -> mock(RedisAskJobStore.class))
                .withUserConfiguration(JiraAppJobCompletions.class)
                .run(context -> {
                    assertThat(context).hasBean("applicationTaskExecutor");
                    assertThat(context).hasSingleBean(JiraAppJobCompletions.class);
                    pool.set(context.getBean(JiraAppJobCompletions.class).pool());
                    assertThat(pool.get().getCorePoolSize()).isEqualTo(1);
                    assertThat(pool.get().getMaximumPoolSize()).isEqualTo(1);
                    assertThat(pool.get().getQueue().remainingCapacity()).isEqualTo(1_000);
                    assertThat(pool.get().getThreadFactory().newThread(() -> {}).isDaemon())
                            .isTrue();
                    assertThat(pool.get().getRejectedExecutionHandler())
                            .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
                });

        assertThat(pool.get().isShutdown()).isTrue();
        assertThatThrownBy(() -> pool.get().execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void stopFinishesTheQueuedWritesInsteadOfDroppingThem_AC_010_17() throws Exception {
        var completions = new JiraAppJobCompletions();
        var release = new CountDownLatch(1);
        var written = new AtomicInteger();
        completions.executor().execute(() -> {
            await(release);
            written.incrementAndGet();
        });
        completions.executor().execute(written::incrementAndGet);
        release.countDown();

        completions.stop();

        assertThat(written).hasValue(2);
    }

    @Test
    void aRejectedWriteRunsOnTheFallbackNotTheCallingThread_AC_010_17() {
        var ran = new AtomicReference<String>();
        Executor fallback = task -> {
            ran.set("fallback");
            task.run();
        };
        Executor full = task -> {
            throw new RejectedExecutionException();
        };

        JiraAppJobCompletions.withFallback(full, fallback).execute(() -> {});

        assertThat(ran).hasValue("fallback");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
