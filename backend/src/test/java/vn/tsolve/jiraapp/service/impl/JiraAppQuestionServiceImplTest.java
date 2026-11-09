package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.exception.JiraAskerNotLinkedException;
import vn.tsolve.jiraapp.service.JiraAskerLookup;

class JiraAppQuestionServiceImplTest {

    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null);
    private static final TSolveUser USER =
            new TSolveUser(UUID.fromString("10000000-0000-0000-0000-000000000001"), "an@t-solve.vn", "An", Set.of());
    private static final ChatbotAnswerResponse ANSWER = new ChatbotAnswerResponse(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            Outcome.ANSWERED,
            "Restart the VPN [1]",
            List.of());

    private final JiraAskerLookup askers = mock(JiraAskerLookup.class);
    private final ChatbotAnswerService answers = mock(ChatbotAnswerService.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void asksOnItsOwnPoolNotTheSharedTaskExecutor_BUG_183() {
        Constructor<?> springConstructor = Arrays.stream(JiraAppQuestionServiceImpl.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .findFirst()
                .orElseThrow();

        assertThat(springConstructor.getParameterTypes()).doesNotContain(Executor.class);
    }

    @Test
    void buildsABoundedPoolThatRefusesWhenFull_BUG_183() {
        ThreadPoolExecutor pool = JiraAppQuestionServiceImpl.askPool(3);
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(3);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(3);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(3);
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void answersMsg06AtOnceWhenItsPoolIsFull_BUG_183() throws Exception {
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
        CountDownLatch release = new CountDownLatch(1);
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));
        when(answers.ask(any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return ANSWER;
        });
        JiraAppQuestionServiceImpl service =
                new JiraAppQuestionServiceImpl(askers, answers, one, Duration.ofSeconds(5));
        try {
            CompletableFuture<ChatbotAnswerResponse> first = CompletableFuture.supplyAsync(
                    () -> service.ask(CALLER, new JiraAppQuestionRequest("q1", List.of())));
            long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (one.getActiveCount() < 1 && System.nanoTime() < waitUntil) {
                Thread.onSpinWait();
            }

            long start = System.nanoTime();
            assertThatThrownBy(() -> service.ask(CALLER, new JiraAppQuestionRequest("q2", List.of())))
                    .isInstanceOf(AiUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(ANSWER);
        } finally {
            release.countDown();
            one.shutdownNow();
        }
    }

    @Test
    void returnsTheChatbotAnswerForTheLinkedUser_AC_010_4() {
        List<ChatbotTurnRequest> history = List.of(new ChatbotTurnRequest("VPN fails", "Check the client [1]"));
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));
        when(answers.ask(any(), any())).thenReturn(ANSWER);

        ChatbotAnswerResponse answer =
                service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("Still failing", history));

        assertThat(answer).isSameAs(ANSWER);
        verify(answers).ask(USER, new ChatbotQuestionRequest("Still failing", history));
    }

    @Test
    void sendsNoHistoryAsAnEmptyHistory_AC_010_4() {
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));
        when(answers.ask(any(), any())).thenReturn(ANSWER);

        service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("VPN fails", null));

        verify(answers).ask(USER, new ChatbotQuestionRequest("VPN fails", List.of()));
    }

    @Test
    void refusesAnUnlinkedAskerWithoutCallingTheAi_AC_010_11() {
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(JiraAskerNotLinkedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("MSG62");
                });
        verifyNoInteractions(answers);
    }

    @Test
    void refusesAFitWithoutAPrincipalWithoutCallingTheAi_AC_010_11() {
        JiraAppPrincipal productEvent = new JiraAppPrincipal("cloud-1", null, null);

        assertThatThrownBy(() ->
                        service(Duration.ofSeconds(5)).ask(productEvent, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(JiraAskerNotLinkedException.class);
        verifyNoInteractions(askers, answers);
    }

    @Test
    void answersMsg06WhenTheAiIsUnavailable_AC_010_8() {
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));
        when(answers.ask(any(), any())).thenThrow(new AiUnavailableException());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("MSG06");
                });
    }

    @Test
    void answersMsg06WhenTheChatbotMissesTheDeadline_AC_010_8() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));
        when(answers.ask(any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return ANSWER;
        });

        long start = System.nanoTime();
        assertThatThrownBy(
                        () -> service(Duration.ofMillis(100)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        release.countDown();
    }

    @Test
    void reportsALinkedUser_AC_010_2() {
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.of(USER));

        assertThat(service(Duration.ofSeconds(5)).eligibility(CALLER).linked()).isTrue();
        verifyNoInteractions(answers);
    }

    @Test
    void reportsAnUnlinkedUser_AC_010_2() {
        when(askers.find("cloud-1", "jira-1")).thenReturn(Optional.empty());

        assertThat(service(Duration.ofSeconds(5)).eligibility(CALLER).linked()).isFalse();
    }

    @Test
    void reportsAFitWithoutAPrincipalAsUnlinked_AC_010_2() {
        assertThat(service(Duration.ofSeconds(5))
                        .eligibility(new JiraAppPrincipal("cloud-1", " ", null))
                        .linked())
                .isFalse();
        verifyNoInteractions(askers);
    }

    @Test
    void refusesANonPositiveDeadline() {
        assertThatThrownBy(() -> service(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private JiraAppQuestionServiceImpl service(Duration timeout) {
        return new JiraAppQuestionServiceImpl(askers, answers, executor, timeout);
    }
}
