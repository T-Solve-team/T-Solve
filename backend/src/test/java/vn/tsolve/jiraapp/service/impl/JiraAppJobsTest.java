package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.exception.ChatbotProjectNotActiveException;

class JiraAppJobsTest {

    @Test
    void aTimedOutJobGivesItsThreadBack_AC_010_17() throws Exception {
        var pool = JiraAppJobs.pool(1, "JIRA_APP_ASK_THREADS", "test-");
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        try {
            var future = JiraAppJobs.supply(
                    () -> {
                        started.countDown();
                        try {
                            Thread.sleep(600_000);
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                        }
                        return "late";
                    },
                    pool);
            started.await();

            // What orTimeout does at the deadline; no clock in a unit test (BUG_173).
            future.completeExceptionally(new TimeoutException());

            assertThat(interrupted.await(60, TimeUnit.SECONDS)).isTrue();
            assertThat(JiraAppJobs.supply(() -> "next", pool).get(60, TimeUnit.SECONDS))
                    .isEqualTo("next");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aJobDeadlineMustEndBeforeTheJiraAppStopsPolling_AC_010_17() {
        assertThat(JiraAppJobs.jobTimeout(Duration.ofSeconds(70), "JIRA_APP_ASK_TIMEOUT"))
                .isEqualTo(Duration.ofSeconds(70));
        for (var timeout : new Duration[] {Duration.ZERO, Duration.ofSeconds(71), Duration.ofSeconds(301)}) {
            assertThatThrownBy(() -> JiraAppJobs.jobTimeout(timeout, "JIRA_APP_ASK_TIMEOUT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("JIRA_APP_ASK_TIMEOUT");
        }
    }

    @Test
    void keepsOnlyTheCodeOfARefusal_AC_010_17() {
        assertThat(JiraAppJobs.refusalCode(new CompletionException(new ChatbotProjectNotActiveException())))
                .isEqualTo("MSG08");
        assertThat(JiraAppJobs.refusalCode(new AiUnavailableException())).isNull();
        assertThat(JiraAppJobs.refusalCode(new TimeoutException())).isNull();
    }
}
