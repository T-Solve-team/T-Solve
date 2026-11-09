package vn.tsolve.chatbot.source.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;

class NoWikiSourcesTest {
    @Test
    void failsClosed_AC_006_7() {
        WikiSources sources = new NoWikiSources();
        TSolveUser user = new TSolveUser(new UUID(0, 1), "reader@example.test", "Reader", Set.of());
        Set<UUID> ids = Set.of(new UUID(0, 2), new UUID(0, 3));

        assertThat(sources.nearest(user, new float[1024], 0.7, 5)).isEmpty();
        assertThat(sources.unavailable(user, ids)).containsExactlyInAnyOrderElementsOf(ids);
    }
}
