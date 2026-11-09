package vn.tsolve.chatbot.source.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.security.TSolveUser;

class NoWikiSourcesTest {
    private static final TSolveUser USER = new TSolveUser(UUID.randomUUID(), "asker@example.test", "Asker", Set.of());
    private final NoWikiSources wiki = new NoWikiSources();

    @Test
    void failsClosed_AC_006_7() {
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());

        assertThat(wiki.nearest(USER, new float[1024], 0.0, 5)).isEmpty();
        assertThat(wiki.unavailable(USER, ids)).isEqualTo(ids);
    }
}
