package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.error.MessageCodeException;
import vn.tsolve.common.error.NotFoundException;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.service.CsvImportService;
import vn.tsolve.ticket.service.ImportConfirmService;

/** Confirm against a real PostgreSQL: what mocks cannot prove (rollback, after-commit, unique constraint). */
@SpringBootTest
@Testcontainers
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=pm-a:1;pm-b:1;boom-pm:1",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class ImportConfirmIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static final String HEADER = "ticket_id,title,symptoms,root_cause,resolution_steps,scope\n";

    @Autowired
    private CsvImportService csvImports;

    @Autowired
    private ImportConfirmService confirms;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublishedEvents published;

    @TestConfiguration
    static class Listener {
        @Bean
        PublishedEvents publishedEvents(DataSource dataSource) {
            return new PublishedEvents(dataSource);
        }
    }

    /**
     * Listens the way TicketsPublishedEvent asks (AFTER_COMMIT) and records how many tickets a separate database
     * connection already sees; it can be told to fail, like a broken search listener.
     */
    static class PublishedEvents {
        final List<String> seen = new CopyOnWriteArrayList<>();
        volatile boolean failNext;
        private final DataSource dataSource;

        PublishedEvents(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(TicketsPublishedEvent event) throws SQLException {
            // a connection of its own, not the one bound to the import transaction
            try (Connection c = dataSource.getConnection();
                    ResultSet r = c.createStatement().executeQuery("select count(*) from ticket")) {
                r.next();
                seen.add("ids=" + event.ticketIds().size() + " visible=" + r.getInt(1));
            }
            if (failNext) {
                throw new IllegalStateException("search down");
            }
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from ticket_review_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("delete from import_preview");
        jdbc.execute("drop trigger if exists fail_for_boom on ticket_review_decision");
        published.seen.clear();
        published.failNext = false;
    }

    private long upload(String user, String... ids) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (String id : ids) {
            csv.append(id).append(",Login fails,Error 500,Bad token,Restart,All users\n");
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "tickets.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));
        return csvImports.upload(user, 1L, file).previewId();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8() {
        // the third insert of the import (the review decisions) fails inside the database
        jdbc.execute("create or replace function fail_for_boom() returns trigger language plpgsql as "
                + "$$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger fail_for_boom before insert on ticket_review_decision "
                + "for each row when (new.reviewer = 'boom-pm') execute function fail_for_boom()");
        long preview = upload("boom-pm", "T-1", "T-2");

        MessageCodeException e =
                catchThrowableOfType(() -> confirms.confirm("boom-pm", preview), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(count("ticket")).isZero();
        assertThat(count("ticket_review_decision")).isZero();
        assertThat(jdbc.queryForList("select status, published_count from import_batch"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("status")).isEqualTo("FAILED");
                    assertThat(row.get("published_count")).isEqualTo(0);
                });
        assertThat(count("import_preview")).isEqualTo(1);
        assertThat(published.seen).isEmpty();
    }

    @Test
    void eventArrivesAfterTheTicketsAreCommitted_AC_004_10() {
        long preview = upload("pm-a", "T-1", "T-2", "T-3");

        confirms.confirm("pm-a", preview);

        assertThat(published.seen).containsExactly("ids=3 visible=3");
        assertThat(count("ticket")).isEqualTo(3);
        assertThat(count("ticket_review_decision")).isEqualTo(3);
        assertThat(count("import_preview")).isZero();
    }

    @Test
    void aListenerFailingAfterCommitDoesNotUndoTheImport_AC_004_10() {
        published.failNext = true;
        long preview = upload("pm-a", "T-1", "T-2");

        ImportResultResponse result = confirms.confirm("pm-a", preview);

        assertThat(result.created()).isEqualTo(2);
        assertThat(count("ticket")).isEqualTo(2);
        assertThat(jdbc.queryForList("select status from import_batch", String.class))
                .containsExactly("COMPLETED");
    }

    @Test
    void secondConfirmOfTheSamePreviewIsNotFound_AC_004_12() throws Exception {
        long preview = upload("pm-a", "T-1", "T-2");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> confirm = () -> {
            start.await();
            try {
                confirms.confirm("pm-a", preview);
                return "ok";
            } catch (NotFoundException e) {
                return "404";
            } catch (MessageCodeException e) {
                return e.getCode();
            }
        };
        Future<String> a = pool.submit(confirm);
        Future<String> b = pool.submit(confirm);
        start.countDown();

        List<String> outcomes = List.of(a.get(), b.get());
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("ok", "404");
        assertThat(count("ticket")).isEqualTo(2);
        assertThat(jdbc.queryForList("select status from import_batch", String.class))
                .containsExactly("COMPLETED");
        assertThat(published.seen).containsExactly("ids=2 visible=2");
    }

    @Test
    void uniqueTicketIdIsTheLastGuardAgainstTwoConfirmsAtOnce() throws Exception {
        long first = upload("pm-a", "T-1", "T-2");
        long second = upload("pm-b", "T-1", "T-2");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> confirmA = () -> {
            start.await();
            return outcome(() -> confirms.confirm("pm-a", first));
        };
        Callable<String> confirmB = () -> {
            start.await();
            return outcome(() -> confirms.confirm("pm-b", second));
        };
        Future<String> a = pool.submit(confirmA);
        Future<String> b = pool.submit(confirmB);
        start.countDown();

        List<String> outcomes = List.of(a.get(), b.get());
        pool.shutdown();

        // one import wins; the other fails as MSG33 whether the check or the constraint caught it
        assertThat(outcomes).containsOnlyOnce("ok").contains("MSG33");
        assertThat(count("ticket")).isEqualTo(2);
        assertThat(jdbc.queryForList("select status from import_batch order by status", String.class))
                .containsExactly("COMPLETED", "FAILED");
    }

    @Test
    void databaseRejectsADuplicateTicketId() {
        jdbc.update("insert into ticket (ticket_id, project_id, source, status, title, symptoms, root_cause,"
                + " resolution_steps, scope) values ('T-9', 1, 'CSV', 'PUBLISHED', 't', 's', 'r', 'st', 'sc')");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into ticket (ticket_id, project_id, source, status, title, symptoms, root_cause,"
                                + " resolution_steps, scope) values ('T-9', 1, 'CSV', 'PUBLISHED', 't', 's', 'r',"
                                + " 'st', 'sc')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private static String outcome(Runnable call) {
        try {
            call.run();
            return "ok";
        } catch (MessageCodeException e) {
            return e.getCode();
        }
    }
}
