package vn.tsolve.jiraapp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraAppJobStatus;

@Component
class RedisAskJobStore {
    // Well past any job deadline: JiraAppJobs.jobTimeout keeps those at 70 s or less.
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
            // E.g. a job the previous release wrote: unavailable like a Redis failure, not a 500.
            throw new DataRetrievalFailureException("Cannot read Jira App job", e);
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

    /** {@code code}: the refusal of a FAILED job (see JiraAppJobs.refusalCode); null otherwise. */
    record Job<T>(
            String cloudId, String accountId, String projectKey, JiraAppJobStatus status, T payload, String code) {
        Job(String cloudId, String accountId, String projectKey, JiraAppJobStatus status, T payload) {
            this(cloudId, accountId, projectKey, status, payload, null);
        }

        /** {@code inProject}: only that Jira project may read it (Ask, AC-010.13); a rewrite may not (AC-007.6). */
        static <T> Job<T> pending(JiraAppPrincipal caller, boolean inProject) {
            return new Job<>(
                    caller.cloudId(),
                    caller.accountId(),
                    inProject ? caller.projectKey() : null,
                    JiraAppJobStatus.PENDING,
                    null);
        }

        Job<T> done(T payload) {
            return new Job<>(cloudId, accountId, projectKey, JiraAppJobStatus.DONE, payload, null);
        }

        Job<T> failed(Throwable error) {
            return new Job<>(
                    cloudId, accountId, projectKey, JiraAppJobStatus.FAILED, null, JiraAppJobs.refusalCode(error));
        }

        /** Only the account that started the job, in the same site (and Jira project, if it has one), may read it. */
        boolean ownedBy(JiraAppPrincipal caller) {
            return caller != null
                    && Objects.equals(cloudId, caller.cloudId())
                    && Objects.equals(accountId, caller.accountId())
                    && (projectKey == null || projectKey.equals(caller.projectKey()));
        }
    }
}
