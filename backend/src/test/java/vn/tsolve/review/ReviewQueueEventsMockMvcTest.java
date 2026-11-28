package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.SpringBootMockMvcBuilderCustomizer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ReviewQueueEventsMockMvcTest {

    /**
     * BUG-186 and BUG-289 share this opening path. Force the first SSE write between creation and
     * traversal of MockMvc diagnostics' live header iterator, without Docker or a scheduler delay.
     * The initial flush must signal readiness before any heartbeat is sent.
     */
    @Test
    void opensAndClosesWhileTheSenderWritesHeaders_BUG_197() throws Exception {
        try (var context = new StaticWebApplicationContext();
                var sender = Executors.newSingleThreadExecutor()) {
            var controller = new StreamController();
            var builder = MockMvcBuilders.standaloneSetup(controller);
            var customizer = new SpringBootMockMvcBuilderCustomizer(context);
            customizer.setAddFilters(false);
            // Exercise the IT's actual diagnostics policy, including the Boot result handler.
            customizer.setPrint(ReviewQueueEventsIT.class
                    .getAnnotation(AutoConfigureMockMvc.class)
                    .print());
            customizer.setPrintOnlyOnFailure(ReviewQueueEventsIT.class
                    .getAnnotation(AutoConfigureMockMvc.class)
                    .printOnlyOnFailure());
            customizer.customize(builder);
            var mvc = builder.build();

            for (int i = 0; i < 100; i++) {
                var write = new CountDownLatch(1);
                var emitter = new SseEmitter(0L);
                controller.emitter = emitter;
                Future<?> sent = sender.submit(() -> {
                    write.await();
                    emitter.send(SseEmitter.event().comment("open"));
                    return null;
                });
                var response = new InterleavingResponse(write, sent);
                response.setHeader("X-first", "one");
                response.setHeader("X-second", "two");
                try {
                    var result = mvc.perform(ReviewQueueEventsIT.openingRequest(get("/events"), response))
                            .andReturn();
                    write.countDown();
                    sent.get();
                    assertThat(result.getRequest().isAsyncStarted()).isTrue();
                    response.awaitOpen();
                    assertThat(response.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
                    assertThat(response.getContentAsString()).isEqualTo(":open\n\n");
                    emitter.send(SseEmitter.event().comment("hb"));
                    assertThat(response.getContentAsString()).isEqualTo(":open\n\n:hb\n\n");
                    emitter.complete();
                    mvc.perform(asyncDispatch(result));
                    assertThat(result.getRequest().isAsyncStarted()).isFalse();
                } finally {
                    write.countDown();
                    sent.get();
                    emitter.complete();
                }
            }
        }
    }

    @RestController
    static class StreamController {
        private SseEmitter emitter;

        @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter open() {
            return emitter;
        }
    }

    private static final class InterleavingResponse extends ReviewQueueEventsIT.OpeningResponse {
        private final CountDownLatch write;
        private final Future<?> sent;

        InterleavingResponse(CountDownLatch write, Future<?> sent) {
            this.write = write;
            this.sent = sent;
        }

        @Override
        public Collection<String> getHeaderNames() {
            Collection<String> names = super.getHeaderNames();
            Iterator<String> iterator = names.iterator();
            write.countDown();
            try {
                sent.get();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            return new AbstractCollection<>() {
                @Override
                public Iterator<String> iterator() {
                    return iterator;
                }

                @Override
                public int size() {
                    return names.size();
                }
            };
        }
    }
}
