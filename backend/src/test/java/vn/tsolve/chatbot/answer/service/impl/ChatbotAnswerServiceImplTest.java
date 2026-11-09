package vn.tsolve.chatbot.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

@ExtendWith(OutputCaptureExtension.class)
class ChatbotAnswerServiceImplTest {
    private static final UUID ANSWER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final TSolveUser USER = new TSolveUser(UUID.randomUUID(), "asker@example.test", "Asker", Set.of());
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final SolutionRetrieval solutions = mock(SolutionRetrieval.class);
    private final WikiSources wiki = mock(WikiSources.class);
    private final ChatbotAnswerRepository answers = mock(ChatbotAnswerRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final float[] vector = new float[1024];
    private ChatbotAnswerServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = service(Duration.ofSeconds(2), 5);
        when(answers.save(any())).thenAnswer(invocation -> {
            ChatbotAnswer answer = invocation.getArgument(0);
            ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
            return answer;
        });
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void embedsOnceAndAsksBothSources_AC_005_1() {
        Thread requestThread = Thread.currentThread();
        when(embeddings.embed(List.of("question"))).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isNotSameAs(requestThread);
            return List.of(vector);
        });
        when(solutions.nearest(vector, 0.7, 5)).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isSameAs(requestThread);
            return List.of(solution("solution", 0.9));
        });
        when(wiki.nearest(USER, vector, 0.7, 5)).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isSameAs(requestThread);
            return List.of();
        });
        when(llm.chat(any(), isNull())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isNotSameAs(requestThread);
            return "Answer [1]";
        });
        var response = service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  question  ", List.of(new ChatbotTurnRequest("old question", "old answer"))));
        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        verify(embeddings).embed(List.of("question"));
        verify(solutions).nearest(vector, 0.7, 5);
        verify(wiki).nearest(USER, vector, 0.7, 5);
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(prompt.capture(), isNull());
        assertThat(prompt.getValue()).hasSize(2);
        assertThat(prompt.getValue().toString()).doesNotContain("old question", "old answer");
    }

    @Test
    void keepsTopSourcesNumbered_AC_005_2() {
        service = service(Duration.ofSeconds(2), 3);
        UUID document = UUID.randomUUID();
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(solutions.nearest(vector, 0.7, 3))
                .thenReturn(List.of(solution("best", 0.99), solution("tie", 0.9), solution("low", 0.8)));
        when(wiki.nearest(USER, vector, 0.7, 3)).thenReturn(List.of(new WikiPassage(document, "Wiki", "passage", 0.9)));
        when(llm.chat(any(), isNull())).thenReturn("Answer [1] [2] [3]");

        var response = service.ask(USER, question());

        assertThat(response.citations()).hasSize(3);
        var first = (TicketSolutionCitation) response.citations().get(0);
        var second = (TicketSolutionCitation) response.citations().get(1);
        var third = (WikiDocumentCitation) response.citations().get(2);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.solution().symptoms()).isEqualTo("best");
        assertThat(second.number()).isEqualTo(2);
        assertThat(second.solution().symptoms()).isEqualTo("tie");
        assertThat(third.number()).isEqualTo(3);
        assertThat(third.wikiDocumentId()).isEqualTo(document);
        assertThat(third.title()).isEqualTo("Wiki");
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(prompt.capture(), isNull());
        assertThat(prompt.getValue().getLast().content())
                .contains("<source n=\"1\">", "<source n=\"2\">", "<source n=\"3\">")
                .doesNotContain("<source n=\"4\">", "low");
    }

    @Test
    void answersNoSourceWithoutChat_AC_005_4() {
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(solutions.nearest(any(), anyDouble(), anyInt())).thenReturn(List.of());
        when(wiki.nearest(any(), any(), anyDouble(), anyInt())).thenReturn(List.of());
        var response = service.ask(USER, question());
        assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
        assertThat(response.text()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answerId()).isEqualTo(ANSWER_ID);
        verifyNoInteractions(llm);
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("NO_SOURCE");
        assertThat(saved.getValue().getAnswerText()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"No citation", "Unknown [9]", "[01] [ 1 ] [0] [1, 2]", "[100]"})
    void answersNoSourceWhenNoMarker_AC_005_5(String text) {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn(text);
        var response = service.ask(USER, question());
        assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
        assertThat(response.text()).isEmpty();
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void storesOneRowAndReturnsItsId_AC_005_6() {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");
        Thread requestThread = Thread.currentThread();
        doAnswer(invocation -> {
                    assertThat(Thread.currentThread()).isSameAs(requestThread);
                    ChatbotAnswer answer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
                    return answer;
                })
                .when(answers)
                .save(any());
        var response = service.ask(USER, new ChatbotQuestionRequest("  question  ", List.of()));
        assertThat(response.answerId()).isEqualTo(ANSWER_ID);
        assertThat(response.text()).isEqualTo("Answer [1]");
        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER.id());
        assertThat(saved.getValue().getQuestion()).isEqualTo("question");
        assertThat(saved.getValue().getOutcome()).isEqualTo("ANSWERED");
        assertThat(saved.getValue().getAnswerText()).isEqualTo("Answer [1]");
        verify(answers).flush();
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void returnsOnlyCitedSources_AC_006_1() {
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(solutions.nearest(any(), anyDouble(), anyInt()))
                .thenReturn(List.of(solution("first", 0.95), solution("second", 0.9)));
        when(llm.chat(any(), isNull())).thenReturn("Use the second fix [2].");

        var response = service.ask(USER, question());

        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(response.citations()).hasSize(1);
        var cited = (TicketSolutionCitation) response.citations().getFirst();
        assertThat(cited.number()).isEqualTo(2);
        assertThat(cited.solution().symptoms()).isEqualTo("second");
    }

    @Test
    void storesAndReturnsTextWithoutUnknownMarkers_AC_006_2() {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn("Restart [7] the service [1].");

        var response = service.ask(USER, question());

        assertThat(response.text()).isEqualTo("Restart the service [1].");
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getAnswerText()).isEqualTo("Restart the service [1].");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void answers503AndStoresNothing_AC_005_7(boolean embeddingFails) {
        if (embeddingFails) {
            when(embeddings.embed(any())).thenThrow(new AiUnavailableException());
        } else {
            withSolution();
            when(llm.chat(any(), isNull())).thenThrow(new AiUnavailableException());
        }
        assertThatThrownBy(() -> service.ask(USER, question()))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("MSG06");
        verifyNoInteractions(answers);
        verify(embeddings, times(1)).embed(any());
        if (embeddingFails) {
            verifyNoInteractions(solutions, wiki, llm);
        } else {
            verify(llm, times(1)).chat(any(), isNull());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timesOutTheWholeAsk_AC_005_7(boolean embeddingStalls) {
        service = service(Duration.ofMillis(600), 5);
        CountDownLatch stalled = new CountDownLatch(1);
        when(embeddings.embed(any())).thenAnswer(invocation -> {
            if (embeddingStalls) {
                stalled.await();
            } else {
                TimeUnit.MILLISECONDS.sleep(200);
            }
            return List.of(vector);
        });
        if (!embeddingStalls) {
            when(solutions.nearest(any(), anyDouble(), anyInt())).thenAnswer(invocation -> {
                TimeUnit.MILLISECONDS.sleep(200);
                return List.of(solution("solution", 0.9));
            });
            when(llm.chat(any(), isNull())).thenAnswer(invocation -> {
                stalled.await();
                return "Late [1]";
            });
        }
        long start = System.nanoTime();
        try {
            assertThatThrownBy(() -> service.ask(USER, question())).isInstanceOf(AiUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(900));
            verifyNoInteractions(answers);
        } finally {
            stalled.countDown();
        }
    }

    @Test
    void logsNoContent_AC_005_10(CapturedOutput output) {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn("private-answer [1]");
        service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "private-question", List.of(new ChatbotTurnRequest("private-history", "private-turn-answer"))));
        assertThat(output.getAll())
                .doesNotContain(
                        "private-question",
                        "private-history",
                        "private-turn-answer",
                        "private-answer",
                        "private-source");
    }

    @ParameterizedTest
    @CsvSource({
        "NO_SOURCE, false",
        "UNCITED, false",
        "ANSWERED, false",
        "NO_SOURCE, true",
        "UNCITED, true",
        "ANSWERED, true"
    })
    void timesOutAndRollsBackSlowPersistence_BUG_165(String outcome, boolean slowFlush) {
        service = service(Duration.ofMillis(500), 5);
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        if (!outcome.equals("NO_SOURCE")) {
            withSolution();
            when(llm.chat(any(), isNull())).thenReturn(outcome.equals("ANSWERED") ? "Answer [1]" : "No citation");
        }
        doAnswer(invocation -> {
                    if (!slowFlush) {
                        TimeUnit.MILLISECONDS.sleep(600);
                    }
                    ChatbotAnswer answer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
                    return answer;
                })
                .when(answers)
                .save(any());
        doAnswer(invocation -> {
                    if (slowFlush) {
                        TimeUnit.MILLISECONDS.sleep(600);
                    }
                    return null;
                })
                .when(answers)
                .flush();

        assertThatThrownBy(() -> service.ask(USER, question()))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("MSG06")
                .hasNoCause();
        verify(answers).save(any());
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    private ChatbotAnswerServiceImpl service(Duration timeout, int limit) {
        return new ChatbotAnswerServiceImpl(
                embeddings, llm, solutions, wiki, answers, transactions, executor, 0.7, limit, timeout);
    }

    private void withSolution() {
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(solutions.nearest(any(), anyDouble(), anyInt())).thenReturn(List.of(solution("private-source", 0.9)));
    }

    private static RetrievedSolution solution(String symptoms, double similarity) {
        return new RetrievedSolution(symptoms, "cause", "steps", null, "scope", similarity);
    }

    private static ChatbotQuestionRequest question() {
        return new ChatbotQuestionRequest("question", List.of());
    }
}
