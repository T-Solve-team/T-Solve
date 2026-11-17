package vn.tsolve.jiraapp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult.Status;

@Component
class RedisAskJobStore {
    private static final String KEY_PREFIX = "jira-app:ask:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    RedisAskJobStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    void create(UUID id, Job job) {
        redis.opsForValue().set(KEY_PREFIX + id, json(job), TTL);
    }

    Optional<Job> find(UUID id) {
        String json = redis.opsForValue().get(KEY_PREFIX + id);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, Job.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read Jira App question job", e);
        }
    }

    void update(UUID id, Job job) {
        byte[] key = (KEY_PREFIX + id).getBytes(StandardCharsets.UTF_8);
        byte[] value = json(job).getBytes(StandardCharsets.UTF_8);
        // XX also prevents a late completion from recreating an expired job without an expiry (AC-010.17).
        redis.execute((RedisCallback<Boolean>)
                connection -> connection.stringCommands().set(key, value, Expiration.keepTtl(), SetOption.ifPresent()));
    }

    void delete(UUID id) {
        redis.delete(KEY_PREFIX + id);
    }

    private String json(Job job) {
        try {
            return mapper.writeValueAsString(job);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write Jira App question job", e);
        }
    }

    record Job(String cloudId, String accountId, String projectKey, Status status, ChatbotAnswerResponse answer) {
        Job finish(Status status, ChatbotAnswerResponse answer) {
            return new Job(cloudId, accountId, projectKey, status, answer);
        }
    }
}
