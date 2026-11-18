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

@Component
class RedisAskJobStore {
    private static final Duration TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    RedisAskJobStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    <T> void create(String prefix, UUID id, Job<T> job) {
        redis.opsForValue().set(prefix + id, json(job), TTL);
    }

    <T> Optional<Job<T>> find(String prefix, UUID id, Class<T> payloadType) {
        String json = redis.opsForValue().get(prefix + id);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    mapper.readValue(json, mapper.getTypeFactory().constructParametricType(Job.class, payloadType)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read Jira App job", e);
        }
    }

    <T> void update(String prefix, UUID id, Job<T> job) {
        byte[] key = (prefix + id).getBytes(StandardCharsets.UTF_8);
        byte[] value = json(job).getBytes(StandardCharsets.UTF_8);
        // XX prevents a late completion from recreating an expired job (AC-010.17, AC-007.7).
        redis.execute((RedisCallback<Boolean>)
                connection -> connection.stringCommands().set(key, value, Expiration.keepTtl(), SetOption.ifPresent()));
    }

    void delete(String prefix, UUID id) {
        redis.delete(prefix + id);
    }

    private String json(Job<?> job) {
        try {
            return mapper.writeValueAsString(job);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write Jira App job", e);
        }
    }

    enum Status {
        PENDING,
        DONE,
        FAILED
    }

    record Job<T>(String cloudId, String accountId, String projectKey, Status status, T payload) {
        Job<T> finish(Status status, T payload) {
            return new Job<>(cloudId, accountId, projectKey, status, payload);
        }
    }
}
