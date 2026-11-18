package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
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
                .withUserConfiguration(JiraAppJobCompletions.class)
                .run(context -> {
                    assertThat(context).hasBean("applicationTaskExecutor");
                    assertThat(context).hasSingleBean(JiraAppJobCompletions.class);
                    pool.set((ThreadPoolExecutor)
                            context.getBean(JiraAppJobCompletions.class).executor());
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
}
