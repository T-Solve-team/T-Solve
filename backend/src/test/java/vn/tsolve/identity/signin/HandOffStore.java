package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

/** An in-memory {@link HandOffCodeRepository}: {@code save} and {@code findById}, with the saved rows to inspect. */
public final class HandOffStore implements Answer<Object> {

    private final Map<UUID, HandOffCode> rows = new LinkedHashMap<>();
    private final List<HandOffCode> saved = new ArrayList<>();
    private final HandOffCodeRepository repository = Mockito.mock(HandOffCodeRepository.class, this);
    private volatile boolean failOnSave;

    public HandOffCodeRepository repository() {
        return repository;
    }

    public synchronized List<HandOffCode> saved() {
        return List.copyOf(saved);
    }

    public HandOffCode onlySaved() {
        List<HandOffCode> all = saved();
        assertThat(all).as("saved hand-off codes").hasSize(1);
        return all.get(0);
    }

    /** From now on {@code save} and {@code saveAndFlush} throw, as a failing database would. */
    public void failOnSave() {
        failOnSave = true;
    }

    public synchronized void reset() {
        rows.clear();
        saved.clear();
        failOnSave = false;
        Mockito.reset(repository);
    }

    @Override
    public synchronized Object answer(InvocationOnMock invocation) throws Throwable {
        switch (invocation.getMethod().getName()) {
            case "save", "saveAndFlush" -> {
                if (failOnSave) {
                    throw new IllegalStateException("database is down");
                }
                HandOffCode code = invocation.getArgument(0);
                if (code.getId() == null) {
                    ReflectionTestUtils.setField(code, "id", UUID.randomUUID());
                }
                saved.add(code);
                rows.put(code.getId(), code);
                return code;
            }
            case "findById" -> {
                return Optional.ofNullable(rows.get(invocation.getArgument(0)));
            }
            default -> {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        }
    }
}
