package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.WikiDocumentCitation;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult.Status;
import vn.tsolve.jiraapp.service.impl.RedisAskJobStore.Job;

class RedisAskJobStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisAskJobStore store = new RedisAskJobStore(redis, mapper);

    @Test
    void writesPendingOwnerAsJsonWithAtMost300Seconds_AC_010_17() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        Job pending = new Job("cloud", "account", "DP", Status.PENDING, null);

        store.create(id, pending);

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
        assertThat(store.find(id)).contains(pending);
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
        Job done = new Job("cloud", "account", "DP", Status.DONE, answer);

        store.update(id, done);

        var json = ArgumentCaptor.forClass(byte[].class);
        var expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(strings)
                .set(
                        eq(("jira-app:ask:" + id).getBytes(StandardCharsets.UTF_8)),
                        json.capture(),
                        expiry.capture(),
                        eq(RedisStringCommands.SetOption.ifPresent()));
        assertThat(expiry.getValue().isKeepTtl()).isTrue();
        assertThat(mapper.readValue(json.getValue(), Job.class)).isEqualTo(done);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("jira-app:ask:" + id)).thenReturn(new String(json.getValue(), StandardCharsets.UTF_8));
        assertThat(store.find(id)).contains(done);
    }

    @Test
    void returnsEmptyForMissingOrExpiredAndDeletesRejectedJobs_AC_010_16_AC_010_18() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        assertThat(store.find(id)).isEmpty();
        store.delete(id);
        verify(redis).delete("jira-app:ask:" + id);
    }
}
