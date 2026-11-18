package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.jiraapp.dto.JiraAppJobStatus;
import vn.tsolve.jiraapp.service.impl.RedisAskJobStore.Job;

class RedisAskJobStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisAskJobStore store = new RedisAskJobStore(redis, mapper);

    @Test
    void rewritePrefixPreservesTtlAndCannotRecreateExpiredJobs_AC_007_7() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        var pending = new Job<vn.tsolve.ticket.dto.SolutionSections>(
                "cloud", "account", null, JiraAppJobStatus.PENDING, null);
        store.create("jira-app:rewrite:", id, pending);
        var initialJson = ArgumentCaptor.forClass(String.class);
        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("jira-app:rewrite:" + id), initialJson.capture(), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(300));
        assertThat(mapper.readTree(initialJson.getValue()).path("accountId").asText())
                .isEqualTo("account");
        assertThat(mapper.readTree(initialJson.getValue()).path("cloudId").asText())
                .isEqualTo("cloud");

        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(strings);
        doAnswer(call -> ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection))
                .when(redis)
                .execute(any(RedisCallback.class));
        var sections = new vn.tsolve.ticket.dto.SolutionSections("Symptoms", "Cause", "Steps", "", "Scope");
        var done = pending.done(sections);
        store.update("jira-app:rewrite:", id, done);
        var json = ArgumentCaptor.forClass(byte[].class);
        var expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(strings)
                .set(
                        eq(("jira-app:rewrite:" + id).getBytes(StandardCharsets.UTF_8)),
                        json.capture(),
                        expiry.capture(),
                        eq(RedisStringCommands.SetOption.ifPresent()));
        assertThat(expiry.getValue().isKeepTtl()).isTrue();
        when(values.get("jira-app:rewrite:" + id)).thenReturn(new String(json.getValue(), StandardCharsets.UTF_8));
        assertThat(store.find("jira-app:rewrite:", id, vn.tsolve.ticket.dto.SolutionSections.class))
                .contains(done);
        assertThat(store.find("jira-app:ask:", id, ChatbotAnswerResponse.class)).isEmpty();
        store.delete("jira-app:rewrite:", id);
        verify(redis).delete("jira-app:rewrite:" + id);
    }

    @Test
    void anUnreadableJobIsADataAccessFailureNotA500_AC_010_17() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        when(values.get("jira-app:ask:" + id)).thenReturn("{\"status\":\"RENAMED\"}");

        assertThatThrownBy(() -> store.find("jira-app:ask:", id, ChatbotAnswerResponse.class))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void readsAJobWrittenWithoutARefusalCode_AC_010_17() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        when(values.get("jira-app:ask:" + id))
                .thenReturn("{\"cloudId\":\"cloud\",\"accountId\":\"account\",\"projectKey\":\"DP\","
                        + "\"status\":\"FAILED\"}");

        assertThat(store.find("jira-app:ask:", id, ChatbotAnswerResponse.class))
                .contains(new Job<>("cloud", "account", "DP", JiraAppJobStatus.FAILED, null));
    }

    @Test
    void writesPendingOwnerAsJsonWithAtMost300Seconds_AC_010_17() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        Job<ChatbotAnswerResponse> pending = new Job<>("cloud", "account", "DP", JiraAppJobStatus.PENDING, null);

        store.create("jira-app:ask:", id, pending);

        var json = ArgumentCaptor.forClass(String.class);
        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("jira-app:ask:" + id), json.capture(), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(300));
        var tree = mapper.readTree(json.getValue());
        assertThat(tree.path("cloudId").asText()).isEqualTo("cloud");
        assertThat(tree.path("accountId").asText()).isEqualTo("account");
        assertThat(tree.path("projectKey").asText()).isEqualTo("DP");
        assertThat(tree.path("status").asText()).isEqualTo("PENDING");
        when(values.get("jira-app:ask:" + id)).thenReturn(json.getValue());
        assertThat(store.find("jira-app:ask:", id, ChatbotAnswerResponse.class)).contains(pending);
    }

    @Test
    void updatesWithKeepTtlOnlyIfKeyStillExists_AC_010_17() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(strings);
        doAnswer(call -> {
                    RedisCallback<?> callback = call.getArgument(0);
                    return callback.doInRedis(connection);
                })
                .when(redis)
                .execute(any(RedisCallback.class));
        UUID id = UUID.randomUUID();
        var answer = new ChatbotAnswerResponse(
                UUID.randomUUID(),
                ChatbotAnswerResponse.Outcome.ANSWERED,
                "Answer [1]",
                List.of(new WikiDocumentCitation(1, UUID.randomUUID(), "Wiki title")));
        Job<ChatbotAnswerResponse> done = new Job<>("cloud", "account", "DP", JiraAppJobStatus.DONE, answer);

        store.update("jira-app:ask:", id, done);

        var json = ArgumentCaptor.forClass(byte[].class);
        var expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(strings)
                .set(
                        eq(("jira-app:ask:" + id).getBytes(StandardCharsets.UTF_8)),
                        json.capture(),
                        expiry.capture(),
                        eq(RedisStringCommands.SetOption.ifPresent()));
        assertThat(expiry.getValue().isKeepTtl()).isTrue();
        assertThat(mapper.<Job<ChatbotAnswerResponse>>readValue(
                        json.getValue(),
                        mapper.getTypeFactory().constructParametricType(Job.class, ChatbotAnswerResponse.class)))
                .isEqualTo(done);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("jira-app:ask:" + id)).thenReturn(new String(json.getValue(), StandardCharsets.UTF_8));
        assertThat(store.find("jira-app:ask:", id, ChatbotAnswerResponse.class)).contains(done);
    }

    @Test
    void returnsEmptyForMissingOrExpiredAndDeletesRejectedJobs_AC_010_16_AC_010_18() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        assertThat(store.find("jira-app:ask:", id, ChatbotAnswerResponse.class)).isEmpty();
        store.delete("jira-app:ask:", id);
        verify(redis).delete("jira-app:ask:" + id);
    }
}
