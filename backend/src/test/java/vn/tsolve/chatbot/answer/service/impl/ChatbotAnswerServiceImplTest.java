package vn.tsolve.chatbot.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import vn.tsolve.chatbot.answer.dto.ChatbotCitation;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.dto.TicketSolutionCitation;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.exception.ChatbotProjectNotActiveException;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.chatbot.source.service.WikiSources.WikiPassage;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.retrieval.dto.RetrievedSolution;
import vn.tsolve.search.retrieval.dto.RetrievedTicketSolution;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;
import vn.tsolve.ticket.service.ProjectAccess;

@ExtendWith(OutputCaptureExtension.class)
class ChatbotAnswerServiceImplTest {
    private static final UUID ANSWER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
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
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"QUESTION\",\"query\":\"\"}");
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

    @ParameterizedTest
    @ValueSource(strings = {"  HELLO!! 👋🏽 ", "Cảm ơn!! 🙏", "thank-you", "ca\u0309m o\u031bn"})
    void greetingCallsNoAi_AC_009_1(String question) {
        var response = service.ask(USER, new ChatbotQuestionRequest(question, List.of()));
        assertThat(response.outcome()).isEqualTo(Outcome.GREETING);
        assertThat(response.text()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answerId()).isEqualTo(ANSWER_ID);
        verifyNoInteractions(llm, embeddings, solutions, wiki);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF_TOPIC", "INJECTION"})
    void refusesOffTopicAndInjectionWithoutSearch_AC_009_3(String category) {
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"" + category + "\",\"query\":\"\"}");
        var response = service.ask(USER, question());
        assertThat(response.outcome()).isEqualTo(Outcome.valueOf(category));
        assertThat(response.text()).isEmpty();
        assertThat(response.citations()).isEmpty();
        verify(llm).chat(any(), notNull());
        verifyNoMoreInteractions(llm);
        verifyNoInteractions(embeddings, solutions, wiki);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GREETING", "OFF_TOPIC", "INJECTION"})
    void storesClassifiedRowWithEmptyText_AC_009_4(String category) {
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"" + category + "\",\"query\":\"\"}");
        Thread requestThread = Thread.currentThread();
        doAnswer(invocation -> {
                    assertThat(Thread.currentThread()).isSameAs(requestThread);
                    ChatbotAnswer saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", ANSWER_ID);
                    return saved;
                })
                .when(answers)
                .save(any());
        var response = service.ask(USER, new ChatbotQuestionRequest("  message  ", List.of()));
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER.id());
        assertThat(saved.getValue().getQuestion()).isEqualTo("message");
        assertThat(saved.getValue().getOutcome()).isEqualTo(category);
        assertThat(saved.getValue().getAnswerText()).isEmpty();
        assertThat(response.answerId()).isEqualTo(ANSWER_ID);
        verify(answers).flush();
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void searchesWithQueryAnswersWithQuestion_AC_009_5() {
        withSolution();
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"QUESTION\",\"query\":\"  VPN Windows setup  \"}");
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");
        var response = service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  What about Windows?  ",
                        List.of(new ChatbotTurnRequest("How do I connect to VPN?", "earlier answer"))));
        verify(embeddings).embed(List.of("VPN Windows setup"));
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(prompt.capture(), isNull());
        assertThat(prompt.getValue().getLast().content()).contains("<question>What about Windows?</question>");
        assertThat(prompt.getValue()).extracting(ChatMessage::content).allSatisfy(text -> assertThat(text)
                .doesNotContain("VPN Windows setup"));
        assertThat(response.text()).isEqualTo("Answer [1]");
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getQuestion()).isEqualTo("What about Windows?");
        assertThat(saved.getValue().getAnswerText()).isEqualTo("Answer [1]");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "prose",
                "{",
                "{\"category\":\"OTHER\",\"query\":\"ignored\"}",
                "{\"category\":\"QUESTION\",\"query\":\"  \"}"
            })
    void fallsBackToTodaysRetrievalText_AC_009_6(String json) {
        when(llm.chat(any(), notNull())).thenReturn(json);
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        var response = service.ask(USER, new ChatbotQuestionRequest("  VPN question  ", List.of()));
        verify(embeddings).embed(List.of("VPN question"));
        assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
        verify(llm).chat(any(), notNull());
    }

    @Test
    void classifierAiUnavailableFailsOpen_AC_009_6() {
        withSolution();
        when(llm.chat(any(), notNull())).thenThrow(new AiUnavailableException());
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");
        var response = service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  new question  ",
                        List.of(new ChatbotTurnRequest("  previous question  ", "earlier answer"))));
        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        verify(embeddings).embed(List.of("previous question\nnew question"));
        verify(llm).chat(any(), notNull());
        verify(llm).chat(any(), isNull());
        verifyNoMoreInteractions(llm);
    }

    @Test
    void classifierTimeoutFailsOpen_AC_009_7_BUG_329() {
        // Simulate the exceptional completion of a timed-out classifier future without waiting on a clock.
        when(llm.chat(any(), notNull())).thenAnswer(invocation -> {
            throw new TimeoutException();
        });
        var response = service.ask(USER, question());
        assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
        verify(embeddings).embed(List.of("question"));
    }

    @Test
    void classifierUnexpectedFailureFailsOpen_AC_009_6_BUG_330() {
        when(llm.chat(any(), notNull())).thenThrow(new IllegalStateException("unexpected response shape"));
        var response = service.ask(USER, question());
        assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
        verify(embeddings).embed(List.of("question"));
    }

    @Test
    void firstQuestionIsEmbeddedAsAsked_AC_009_5_BUG_331() {
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"QUESTION\",\"query\":\"VPN connection error\"}");
        service.ask(USER, new ChatbotQuestionRequest("  lỗi VPN 809 khi kết nối  ", List.of()));
        verify(embeddings).embed(List.of("lỗi VPN 809 khi kết nối"));
    }

    @Test
    void classifierCalledOnceBeforeEmbeddingWithSchema_AC_009_2() {
        withSolution();
        Thread requestThread = Thread.currentThread();
        when(llm.chat(any(), notNull())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isNotSameAs(requestThread);
            return "{\"category\":\"QUESTION\",\"query\":\"VPN reset\"}";
        });
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");
        var response = service.ask(USER, new ChatbotQuestionRequest("hi, how do I reset my VPN?", List.of()));
        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        var ordered = inOrder(llm, embeddings, solutions, wiki);
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        ordered.verify(llm).chat(prompt.capture(), eq(ClassifierPrompt.CLASSIFIER_SCHEMA));
        assertThat(prompt.getValue().getLast().content()).isEqualTo("<question>hi, how do I reset my VPN?</question>");
        ordered.verify(embeddings).embed(List.of("hi, how do I reset my VPN?"));
        ordered.verify(solutions).nearest(vector, 0.7, 5);
        ordered.verify(wiki).nearest(USER, vector, 0.7, 5);
        ordered.verify(llm).chat(any(), isNull());
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void classifierGreetingAnswersGreeting_AC_009_3() {
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\" greeting \",\"query\":\"\"}");
        var response = service.ask(USER, new ChatbotQuestionRequest("How are you today?", List.of()));
        assertThat(response.outcome()).isEqualTo(Outcome.GREETING);
        assertThat(response.text()).isEmpty();
        assertThat(response.citations()).isEmpty();
        verify(llm).chat(any(), notNull());
        verifyNoMoreInteractions(llm);
        verifyNoInteractions(embeddings, solutions, wiki);
    }

    @Test
    void neverLogsQuery_AC_009_5(CapturedOutput output) {
        withSolution();
        when(llm.chat(any(), notNull()))
                .thenReturn("{\"category\":\"QUESTION\",\"query\":\"private-rewritten-query\"}");
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");
        var response = service.ask(USER, followUp());
        verify(embeddings).embed(List.of("private-rewritten-query"));
        assertThat(output.getAll()).doesNotContain("private-rewritten-query");
        assertThat(response.toString()).doesNotContain("private-rewritten-query");
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getQuestion()).isEqualTo("question");
        assertThat(saved.getValue().getAnswerText()).isEqualTo("Answer [1]");
    }

    @Test
    void embedsQueryOnce_AC_005_1() {
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"QUESTION\",\"query\":\"rewritten query\"}");
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        service.ask(USER, followUp());
        verify(embeddings).embed(List.of("rewritten query"));
        verifyNoMoreInteractions(embeddings);
        verify(solutions).nearest(vector, 0.7, 5);
        verify(wiki).nearest(USER, vector, 0.7, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prose", "{\"category\":\"QUESTION\",\"query\":\"\"}"})
    void followUpFallbackPrependsLastQuestion_AC_007_1(String json) {
        when(llm.chat(any(), notNull())).thenReturn(json);
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  new question  ",
                        List.of(
                                new ChatbotTurnRequest("first question", "first answer"),
                                new ChatbotTurnRequest("  last question  ", "last answer"))));
        verify(embeddings).embed(List.of("last question\nnew question"));
        verify(llm).chat(any(), notNull());
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
        var response = service.ask(USER, new ChatbotQuestionRequest("  question  ", List.of()));
        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        verify(embeddings).embed(List.of("question"));
        verify(solutions).nearest(vector, 0.7, 5);
        verify(wiki).nearest(USER, vector, 0.7, 5);
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(prompt.capture(), isNull());
        assertThat(prompt.getValue()).hasSize(2);
    }

    @Test
    void prependsLastQuestionForRetrieval_AC_007_1() {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");

        service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  What about Windows?  ",
                        List.of(
                                new ChatbotTurnRequest("first question", "first answer"),
                                new ChatbotTurnRequest("  How do I connect to VPN?  ", "last answer"))));

        verify(embeddings).embed(List.of("How do I connect to VPN?\nWhat about Windows?"));
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(prompt.capture(), isNull());
        assertThat(prompt.getValue()).hasSize(6);
        assertThat(prompt.getValue().get(1).content()).isEqualTo("<turn-question>first question</turn-question>");
        assertThat(prompt.getValue().get(2).content()).isEqualTo("<turn-answer>first answer</turn-answer>");
        assertThat(prompt.getValue().getLast().content()).contains("<question>What about Windows?</question>");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storesOnlyTheNewQuestion_AC_007_4(boolean hasSources) {
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        if (hasSources) {
            withSolution();
            when(llm.chat(any(), isNull())).thenReturn("New answer [1]");
        }

        var response = service.ask(
                USER,
                new ChatbotQuestionRequest(
                        "  new question  ", List.of(new ChatbotTurnRequest("earlier question", "earlier answer [1]"))));

        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getQuestion()).isEqualTo("new question");
        assertThat(saved.getValue().getAnswerText()).isEqualTo(hasSources ? "New answer [1]" : "");
        assertThat(response.outcome()).isEqualTo(hasSources ? Outcome.ANSWERED : Outcome.NO_SOURCE);
        if (!hasSources) {
            verify(llm, never()).chat(any(), isNull());
            assertThat(response.citations()).isEmpty();
        }
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
        verify(llm, never()).chat(any(), isNull());
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("NO_SOURCE");
        assertThat(saved.getValue().getAnswerText()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"No citation", "Unknown [9]", "[01] [ 1 ] [0] [n=1]", "[100]"})
    void answersNoSourceWhenNoMarker_AC_005_5(String text) {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn(text);
        var response = service.ask(
                USER,
                new ChatbotQuestionRequest("question", List.of(new ChatbotTurnRequest("old question", "Old [9]"))));
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
    void answersWithCitationsForGroupMarker_AC_006_8_BUG_348() {
        when(embeddings.embed(any())).thenReturn(List.of(vector));
        when(solutions.nearest(any(), anyDouble(), anyInt()))
                .thenReturn(List.of(solution("first", 0.95), solution("second", 0.9)));
        when(llm.chat(any(), isNull())).thenReturn("Map the drive again [1, 2].");

        var response = service.ask(USER, question());

        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(response.text()).isEqualTo("Map the drive again [1][2].");
        assertThat(response.citations()).extracting(ChatbotCitation::number).containsExactly(1, 2);
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getAnswerText()).isEqualTo("Map the drive again [1][2].");
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
            verifyNoInteractions(solutions, wiki);
            verify(llm, never()).chat(any(), isNull());
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

    @Test
    void answersFromTheProjectsDepartmentAndStoresNoUser_AC_012_1_AC_012_2() {
        UUID project = UUID.randomUUID();
        var service = projectService(project, true);
        when(embeddings.embed(List.of("question"))).thenReturn(List.of(vector));
        when(solutions.nearestInProjectDepartment(project, vector, 0.7, 5))
                .thenReturn(List.of(new RetrievedTicketSolution(
                        UUID.randomUUID(), "vpn drops", "cause", "steps", " ", "scope", 0.9)));
        UUID document = UUID.randomUUID();
        when(wiki.nearestCompanyWide(vector, 0.7, 5))
                .thenReturn(List.of(new WikiPassage(document, "VPN guide", "Reconnect", 0.8)));
        when(llm.chat(any(), isNull())).thenReturn("Restart the client [1] and read [2]");

        var response = service.askForProject(project, Optional.empty(), question());

        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0)).isInstanceOf(TicketSolutionCitation.class);
        verify(solutions, never()).nearest(any(), anyDouble(), anyInt());
        verify(wiki, never()).nearest(any(), any(), anyDouble(), anyInt());
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isNull();
        assertThat(saved.getValue().getSource()).isEqualTo(ChatbotAnswer.JIRA_APP);
        assertThat(saved.getValue().getProjectId()).isEqualTo(project);
        assertThat(saved.getValue().getQuestion()).isEqualTo("question");
    }

    @Test
    void classifiesAJiraQuestionBeforeItsSearch_AC_012_1_AC_009_1_AC_009_3() {
        UUID project = UUID.randomUUID();
        var service = projectService(project, true);

        var greeting = service.askForProject(project, Optional.empty(), new ChatbotQuestionRequest("hi", List.of()));
        assertThat(greeting.outcome()).isEqualTo(Outcome.GREETING);
        verifyNoInteractions(llm, embeddings, solutions, wiki);

        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"INJECTION\",\"query\":\"\"}");
        var refused = service.askForProject(project, Optional.empty(), question());
        assertThat(refused.outcome()).isEqualTo(Outcome.INJECTION);
        assertThat(refused.text()).isEmpty();
        verifyNoInteractions(embeddings, solutions, wiki);
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ChatbotAnswer::getSource, ChatbotAnswer::getProjectId, ChatbotAnswer::getOutcome)
                .containsExactly(
                        tuple(ChatbotAnswer.JIRA_APP, project, "GREETING"),
                        tuple(ChatbotAnswer.JIRA_APP, project, "INJECTION"));
    }

    @Test
    void givesALinkedAskerTheWikiTheyMayViewAndStillStoresNoUser_AC_012_1() {
        UUID project = UUID.randomUUID();
        var service = projectService(project, true);
        when(embeddings.embed(List.of("question"))).thenReturn(List.of(vector));
        when(solutions.nearestInProjectDepartment(project, vector, 0.7, 5)).thenReturn(List.of());
        UUID document = UUID.randomUUID();
        when(wiki.nearest(USER, vector, 0.7, 5))
                .thenReturn(List.of(new WikiPassage(document, "Department runbook", "Reconnect", 0.8)));
        when(llm.chat(any(), isNull())).thenReturn("Reconnect [1]");

        var response = service.askForProject(project, Optional.of(USER), question());

        assertThat(response.outcome()).isEqualTo(Outcome.ANSWERED);
        verify(wiki, never()).nearestCompanyWide(any(), anyDouble(), anyInt());
        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isNull();
        assertThat(saved.getValue().getProjectId()).isEqualTo(project);
    }

    @Test
    void storesAWebQuestionWithItsUserAndNoProject_AC_012_2() {
        withSolution();
        when(llm.chat(any(), isNull())).thenReturn("Answer [1]");

        service.ask(USER, question());

        ArgumentCaptor<ChatbotAnswer> saved = ArgumentCaptor.forClass(ChatbotAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER.id());
        assertThat(saved.getValue().getSource()).isEqualTo(ChatbotAnswer.WEB);
        assertThat(saved.getValue().getProjectId()).isNull();
    }

    @Test
    void refusesAnInactiveProjectBeforeAnyAiCall_AC_012_4() {
        UUID project = UUID.randomUUID();
        var service = projectService(project, false);

        assertThatThrownBy(() -> service.askForProject(project, Optional.empty(), question()))
                .isInstanceOf(ChatbotProjectNotActiveException.class)
                .hasMessage("MSG08");
        assertThatThrownBy(() -> service.askForProject(null, Optional.empty(), question()))
                .isInstanceOf(ChatbotProjectNotActiveException.class);
        verifyNoInteractions(embeddings, llm, solutions, wiki, answers);
    }

    @Test
    void ratesOnlyAJiraQuestionOfThatProject_AC_012_7() {
        UUID project = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        var service = projectService(project, true);
        when(answers.updateProjectRating(any(), any(), any(), any())).thenReturn(0);
        when(answers.updateProjectRating(answer, project, ChatbotRating.HELPFUL, NOW))
                .thenReturn(1);

        service.rateForProject(project, answer, ChatbotRating.HELPFUL);

        verify(answers).updateProjectRating(answer, project, ChatbotRating.HELPFUL, NOW);
        assertThatThrownBy(() -> service.rateForProject(UUID.randomUUID(), answer, ChatbotRating.NOT_HELPFUL))
                .isInstanceOf(ChatbotAnswerNotFoundException.class)
                .hasMessage("MSG04");
        assertThatThrownBy(() -> service.rateForProject(project, answer, null))
                .isInstanceOf(ChatbotAnswerNotFoundException.class);
    }

    private ChatbotAnswerServiceImpl projectService(UUID activeProject, boolean active) {
        ProjectAccess projects = mock(ProjectAccess.class);
        when(projects.isActive(activeProject)).thenReturn(active);
        return new ChatbotAnswerServiceImpl(
                embeddings,
                llm,
                solutions,
                wiki,
                answers,
                transactions,
                new ObjectMapper(),
                executor,
                0.7,
                5,
                Duration.ofSeconds(2),
                projects,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ChatbotAnswerServiceImpl service(Duration timeout, int limit) {
        return new ChatbotAnswerServiceImpl(
                embeddings,
                llm,
                solutions,
                wiki,
                answers,
                transactions,
                new ObjectMapper(),
                executor,
                0.7,
                limit,
                timeout);
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

    private static ChatbotQuestionRequest followUp() {
        return new ChatbotQuestionRequest("question", List.of(new ChatbotTurnRequest("earlier", "earlier answer")));
    }
}
