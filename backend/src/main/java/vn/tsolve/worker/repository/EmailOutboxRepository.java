package vn.tsolve.worker.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.worker.dto.OutboundEmail;

public class EmailOutboxRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public EmailOutboxRepository(DataSource jobsDataSource) {
        jdbc = JdbcClient.create(jobsDataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(jobsDataSource));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void insert(UUID id, UUID workspace, OutboundEmail email, Instant now) {
        jdbc.sql("""
                insert into email_outbox (email_outbox_id, workspace_id, recipient, subject, body_text, body_html,
                                          status, attempt_count, next_attempt_at, created_at)
                values (:id, :workspace, :recipient, :subject, :text, :html, 'PENDING', 0, :now, :now)
                """)
                .param("id", id)
                .param("workspace", workspace)
                .param("recipient", email.recipient())
                .param("subject", email.subject())
                .param("text", email.bodyText())
                .param("html", email.bodyHtml(), Types.VARCHAR)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** The callback decides each locked row; the entire batch commits before it is returned for SMTP. */
    public void withDueRows(Instant now, int batchSize, Consumer<List<Row>> claim) {
        withDueRows(now, batchSize, 1, rows -> {
            claim.accept(rows);
            return false;
        });
    }

    /** Read further pages under the same locks until the callback stops or the read limit is reached. */
    public void withDueRows(Instant now, int batchSize, int maxReads, Predicate<List<Row>> claim) {
        transactions.executeWithoutResult(ignored -> {
            for (int read = 0; read < maxReads; read++) {
                List<Row> rows = jdbc.sql("""
                    select * from email_outbox
                    where status = 'PENDING' and next_attempt_at <= :now
                    order by created_at limit :batch for update skip locked
                    """)
                        .param("now", Timestamp.from(now))
                        .param("batch", batchSize)
                        .query((rs, rowNum) -> new Row(
                                rs.getObject("email_outbox_id", UUID.class),
                                rs.getObject("workspace_id", UUID.class),
                                new OutboundEmail(
                                        rs.getString("recipient"),
                                        rs.getString("subject"),
                                        rs.getString("body_text"),
                                        rs.getString("body_html")),
                                rs.getInt("attempt_count"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("claimed_at") == null
                                        ? null
                                        : rs.getTimestamp("claimed_at").toInstant()))
                        .list();
                boolean readMore = claim.test(rows);
                if (rows.isEmpty() || !readMore) break;
            }
        });
    }

    public void claim(Row row, Instant claimedAt, Instant leaseEnd) {
        jdbc.sql("""
                update email_outbox set attempt_count = attempt_count + 1, next_attempt_at = :next, claimed_at = :claimed
                where email_outbox_id = :id and attempt_count = :count and status = 'PENDING'
                """)
                .param("next", Timestamp.from(leaseEnd))
                .param("claimed", Timestamp.from(claimedAt))
                .param("id", row.id())
                .param("count", row.attemptCount())
                .update();
    }

    public void reschedule(Row row, Instant next) {
        jdbc.sql("""
                update email_outbox set next_attempt_at = :next, claimed_at = null
                where email_outbox_id = :id and attempt_count = :count and status = 'PENDING'
                """)
                .param("next", Timestamp.from(next))
                .param("id", row.id())
                .param("count", row.attemptCount())
                .update();
    }

    public void hold(Row row, Instant next) {
        reschedule(row, next);
    }

    public void finish(Row row, String status, Instant finishedAt) {
        jdbc.sql("""
                update email_outbox set status = :status, finished_at = :finished, claimed_at = null
                where email_outbox_id = :id and attempt_count = :count and status = 'PENDING'
                """)
                .param("status", status)
                .param("finished", Timestamp.from(finishedAt))
                .param("id", row.id())
                .param("count", row.attemptCount())
                .update();
    }

    public void deleteFinished(Instant cutoff, int batchSize) {
        jdbc.sql("""
                delete from email_outbox where email_outbox_id in (
                    select email_outbox_id from email_outbox
                    where status <> 'PENDING' and finished_at < :cutoff limit :batch)
                """)
                .param("cutoff", Timestamp.from(cutoff))
                .param("batch", batchSize)
                .update();
    }

    public record Row(
            UUID id, UUID workspaceId, OutboundEmail email, int attemptCount, Instant createdAt, Instant claimedAt) {
        public Row claimed(Instant now) {
            return new Row(id, workspaceId, email, attemptCount + 1, createdAt, now);
        }
    }
}
