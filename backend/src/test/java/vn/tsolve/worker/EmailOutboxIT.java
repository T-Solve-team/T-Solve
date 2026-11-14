package vn.tsolve.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.config.EmailOutboxConfiguration;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.config.WorkerConfiguration;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.impl.EmailOutboxDispatchHandler;
import vn.tsolve.worker.service.impl.EmailOutboxRegistration;
import vn.tsolve.worker.service.impl.EmailOutboxServiceImpl;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class EmailOutboxIT {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");

    @Test
    @DisplayName("AC-004.1 Jobs Liquibase stores one complete PENDING row and survives a second startup")
    void queuesInJobsDatabase() {
        var database = WorkerJobsDatabase.instance();
        var jdbc = new JdbcTemplate(
                new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(WorkerConfiguration.class, EmailOutboxConfiguration.class)
                .withBean(WorkerTenantService.class, () -> tenants)
                .withPropertyValues(
                        "tsolve.worker.enabled=true",
                        "tsolve.worker.jdbc-url=" + database.getJdbcUrl(),
                        "tsolve.worker.username=" + database.getUsername(),
                        "tsolve.worker.password=" + database.getPassword());
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            jdbc.update("delete from email_outbox");
            assertThat(context.getBean(JobRunrRuntime.class).isAutoStartup()).isFalse();
            var service = new EmailOutboxServiceImpl(
                    context.getBean(EmailOutboxRepository.class), tenants, Clock.fixed(NOW, ZoneOffset.UTC));
            tenants.runIn(WORKSPACE, () -> {
                UUID id = service.queue(
                        new OutboundEmail("Writer <writer@example.test>", "Subject", "Text", "<p>Text</p>"));
                var row = jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id);
                assertThat(row)
                        .containsEntry("workspace_id", WORKSPACE)
                        .containsEntry("status", "PENDING")
                        .containsEntry("attempt_count", 0)
                        .containsEntry("recipient", "writer@example.test")
                        .containsEntry("subject", "Subject")
                        .containsEntry("body_text", "Text")
                        .containsEntry("body_html", "<p>Text</p>")
                        .containsEntry("finished_at", null);
                assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(NOW);
                assertThat(((Timestamp) row.get("next_attempt_at")).toInstant()).isEqualTo(NOW);
                assertThatThrownBy(() -> service.queue(
                                new OutboundEmail("a@example.test,b@example.test", "Subject", "Text", null)))
                        .isInstanceOf(InvalidOutboundEmailException.class);
                assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class))
                        .isEqualTo(1);
            });
            assertThat(jdbc.queryForObject(
                            "select count(*) from databasechangelog where id = '001-email-outbox'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForList(
                            "select indexdef from pg_indexes where tablename = 'email_outbox'", String.class))
                    .anySatisfy(index -> assertThat(index).contains("next_attempt_at, created_at", "WHERE", "PENDING"))
                    .anySatisfy(index -> assertThat(index).contains("finished_at", "WHERE", "PENDING"));
        });
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
            assertThat(jdbc.queryForObject(
                            "select count(*) from databasechangelog where id = '001-email-outbox'", Integer.class))
                    .isEqualTo(1);
        });
    }

    private static final EmailOutboxProperties DISPATCH = new EmailOutboxProperties(
            Duration.ofSeconds(30),
            List.of(Duration.ofMinutes(5), Duration.ofMinutes(15)),
            Duration.ofMinutes(5),
            20,
            Duration.ofDays(30));

    private final MutableClock clock = new MutableClock();

    private DriverManagerDataSource jobs() throws Exception {
        var database = WorkerJobsDatabase.instance();
        var dataSource =
                new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
        var migration = new SpringLiquibase();
        migration.setDataSource(dataSource);
        migration.setChangeLog("classpath:db/jobs-changelog/db.changelog-master.yaml");
        migration.afterPropertiesSet();
        new JdbcTemplate(dataSource).update("delete from email_outbox");
        return dataSource;
    }

    private EmailOutboxDispatchHandler dispatcher(
            EmailOutboxRepository repository, WorkerTenantService tenants, SmtpEmailSender sender) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("sender", sender);
        return new EmailOutboxDispatchHandler(
                repository, tenants, beans.getBeanProvider(SmtpEmailSender.class), DISPATCH, clock);
    }

    private UUID queue(EmailOutboxRepository repository, MockWorkerTenantService tenants, String subject) {
        var service = new EmailOutboxServiceImpl(repository, tenants, clock);
        var id = new AtomicReference<UUID>();
        tenants.runIn(
                WORKSPACE,
                () -> id.set(service.queue(new OutboundEmail("writer@example.test", subject, "Text", null))));
        return id.get();
    }

    @Test
    void sendsNewerActiveRowPast25HeldRows_AC_004_4_BUG_218() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        UUID suspended = UUID.randomUUID();
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of(suspended));
        for (int i = 0; i < 25; i++)
            repository.insert(
                    UUID.randomUUID(),
                    suspended,
                    new OutboundEmail("held@example.test", "Held", "Text", null),
                    NOW.minusSeconds(30 - i));
        UUID active = queue(repository, tenants, "Active");
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();

        dispatcher(repository, tenants, mailpit.sender()).run(new EmailOutboxDispatchRequest());

        assertThat(jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", active))
                .containsEntry("status", "SENT")
                .containsEntry("attempt_count", 1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from email_outbox where status = 'PENDING' and attempt_count = 0",
                        Integer.class))
                .isEqualTo(25);
        assertThat(mailpit.onlyMessage().getSubject()).isEqualTo("Active");
    }

    @Test
    void holdsSuspendedWithoutAttemptThenFailsPastHour_AC_004_9_BUG_222() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(), Set.of(WORKSPACE));
        UUID id = queue(repository, tenants, "Suspended");
        var sender = mock(SmtpEmailSender.class);
        var handler = dispatcher(repository, tenants, sender);

        handler.run(new EmailOutboxDispatchRequest());

        var held = jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id);
        assertThat(held)
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 0)
                .containsEntry("claimed_at", null)
                .containsEntry("finished_at", null);
        assertThat(((Timestamp) held.get("next_attempt_at")).toInstant()).isEqualTo(NOW.plusSeconds(30));
        clock.now = NOW.plusSeconds(3601);
        handler.run(new EmailOutboxDispatchRequest());
        var failed = jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id);
        assertThat(failed)
                .containsEntry("status", "FAILED")
                .containsEntry("attempt_count", 0)
                .containsEntry("claimed_at", null);
        assertThat(((Timestamp) failed.get("finished_at")).toInstant()).isEqualTo(clock.now);
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    @Test
    void recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var database = WorkerJobsDatabase.instance();
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(
                        WorkerConfiguration.class,
                        EmailOutboxConfiguration.class,
                        EmailOutboxDispatchHandler.class,
                        EmailOutboxRegistration.class,
                        Transactions.class)
                .withBean(WorkerTenantService.class, () -> tenants)
                .withBean(SmtpEmailSender.class, mailpit::sender)
                .withBean(
                        "transactionManager",
                        DataSourceTransactionManager.class,
                        () -> new DataSourceTransactionManager(dataSource))
                .withPropertyValues(
                        "spring.profiles.active=worker",
                        "spring.mail.host=" + mailpit.host(),
                        "tsolve.worker.enabled=true",
                        "jobrunr.background-job-server.enabled=true",
                        "tsolve.worker.email.interval=5s",
                        "tsolve.worker.jdbc-url=" + database.getJdbcUrl(),
                        "tsolve.worker.username=" + database.getUsername(),
                        "tsolve.worker.password=" + database.getPassword())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SmtpEmailSender.class);
                    var runtime = context.getBean(JobRunrRuntime.class);
                    assertThat(runtime.isRunning()).isTrue();
                    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(
                                    context.getBean(EmailOutboxDispatchHandler.class)))
                            .isTrue();
                    var service = context.getBean(EmailOutboxService.class);
                    var id = new AtomicReference<UUID>();
                    tenants.runIn(
                            WORKSPACE,
                            () -> id.set(service.queue(
                                    new OutboundEmail("writer@example.test", "Recurring", "Text", null))));
                    // ApplicationContextRunner does not publish ApplicationReadyEvent.
                    context.getBean(EmailOutboxRegistration.class).register();
                    try {
                        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(jdbc.queryForMap(
                                        "select * from email_outbox where email_outbox_id = ?", id.get()))
                                .containsEntry("status", "SENT")
                                .containsEntry("attempt_count", 1));
                        assertThat(mailpit.onlyMessage().getSubject()).isEqualTo("Recurring");
                    } finally {
                        runtime.scheduler().deleteRecurringJob(EmailOutboxRegistration.ID);
                    }
                });
    }

    @Test
    @DisplayName("AC-004.12 AC-004.5 AC-004.6 queue to dispatch to SENT reaches Mailpit outside any transaction")
    void deliversThroughOutbox() throws Exception {
        var dataSource = jobs();
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();
        UUID id = queue(repository, tenants, "Queued message");
        var sender = spy(mailpit.sender());
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    call.callRealMethod();
                    return null;
                })
                .when(sender)
                .send(any());
        dispatcher(repository, tenants, sender).run(new EmailOutboxDispatchRequest());
        var row = new JdbcTemplate(dataSource).queryForMap("select * from email_outbox where email_outbox_id = ?", id);
        assertThat(row)
                .containsEntry("status", "SENT")
                .containsEntry("attempt_count", 1)
                .containsEntry("claimed_at", null);
        assertThat(((Timestamp) row.get("finished_at")).toInstant()).isEqualTo(NOW);
        assertThat(mailpit.onlyMessage().getSubject()).isEqualTo("Queued message");
    }

    @Test
    @DisplayName("AC-004.12 AC-004.7 refusing SMTP retries after five and fifteen minutes then FAILED")
    void retriesRefusedSmtp() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        UUID id = queue(repository, tenants, "Refused message");
        // Reserve an ephemeral local port, then close it so SMTP connections are refused.
        try (var refusing = ServerSocketChannel.open()) {
            refusing.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = ((InetSocketAddress) refusing.getLocalAddress()).getPort();
            refusing.close();
            var mail = new JavaMailSenderImpl();
            mail.setHost(InetAddress.getLoopbackAddress().getHostAddress());
            mail.setPort(port);
            mail.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
            mail.getJavaMailProperties().put("mail.smtp.timeout", "1000");
            mail.getJavaMailProperties().put("mail.smtp.writetimeout", "1000");
            var handler = dispatcher(repository, tenants, new SmtpEmailSender(mail, "worker@example.test"));
            for (int attempt = 1; attempt <= 3; attempt++) {
                handler.run(new EmailOutboxDispatchRequest());
                var row = jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id);
                assertThat(row)
                        .containsEntry("attempt_count", attempt)
                        .containsEntry("claimed_at", null)
                        .containsEntry("status", attempt == 3 ? "FAILED" : "PENDING");
                if (attempt < 3) {
                    handler.run(new EmailOutboxDispatchRequest());
                    assertThat(jdbc.queryForObject(
                                    "select attempt_count from email_outbox where email_outbox_id = ?",
                                    Integer.class,
                                    id))
                            .isEqualTo(attempt);
                    clock.now = clock.now.plus(Duration.ofMinutes(attempt == 1 ? 5 : 15));
                    assertThat(((Timestamp) row.get("next_attempt_at")).toInstant())
                            .isEqualTo(clock.now);
                }
            }
            assertThat(jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id))
                    .containsEntry("status", "FAILED")
                    .containsEntry("attempt_count", 3)
                    .containsEntry("claimed_at", null);
        }
    }

    @Test
    @DisplayName("AC-004.8 AC-004.12 two concurrent dispatch runs send every due row exactly once")
    void concurrentClaims() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        var mailpit = WorkerMailpit.instance();
        mailpit.clear();
        for (int i = 0; i < 25; i++) queue(repository, tenants, "Concurrent " + i);
        var sender = spy(mailpit.sender());
        var sending = new CountDownLatch(2);
        doAnswer(call -> {
                    sending.countDown();
                    assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
                    call.callRealMethod();
                    return null;
                })
                .when(sender)
                .send(any());
        var handler = dispatcher(repository, tenants, sender);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> run = () -> {
                start.await();
                handler.run(new EmailOutboxDispatchRequest());
                return null;
            };
            var first = executor.submit(run);
            var second = executor.submit(run);
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from email_outbox where status = 'SENT' and attempt_count = 1 and claimed_at is null",
                        Integer.class))
                .isEqualTo(25);
        assertThat(mailpit.messages().size()).isEqualTo(25);
        var subjects = new HashSet<String>();
        mailpit.messages()
                .forEach(message -> subjects.add(message.required("Subject").asText()));
        assertThat(subjects).hasSize(25);
    }

    @Test
    @DisplayName("AC-004.4 AC-004.10 AC-004.12 oldest first, bounded batch and terminal-only retention")
    void ordersLimitsAndDeletes() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 22; i++) {
            clock.now = NOW.plusSeconds(i);
            ids.add(queue(repository, tenants, "Ordered " + i));
        }
        var sent = new ArrayList<String>();
        var sender = mock(SmtpEmailSender.class);
        doAnswer(call -> {
                    sent.add(call.<OutboundEmail>getArgument(0).subject());
                    return null;
                })
                .when(sender)
                .send(any());
        var handler = dispatcher(repository, tenants, sender);
        handler.run(new EmailOutboxDispatchRequest());
        assertThat(sent)
                .containsExactlyElementsOf(
                        IntStream.range(0, 20).mapToObj(i -> "Ordered " + i).toList());
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where status = 'PENDING'", Integer.class))
                .isEqualTo(2);
        jdbc.update("update email_outbox set status = 'FAILED' where email_outbox_id = ?", ids.get(0));
        clock.now = clock.now.plus(Duration.ofDays(30));
        repository.deleteFinished(clock.now.minus(Duration.ofDays(30)), 20);
        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class))
                .isEqualTo(22);
        clock.now = clock.now.plusSeconds(1);
        // The two unfinished rows become newly FAILED; old terminal rows are deleted.
        tenants = new MockWorkerTenantService(Set.of(), Set.of(WORKSPACE));
        dispatcher(repository, tenants, sender).run(new EmailOutboxDispatchRequest());
        assertThat(jdbc.queryForList("select email_outbox_id from email_outbox", UUID.class))
                .containsExactlyInAnyOrder(ids.get(20), ids.get(21));
    }

    @Test
    @DisplayName("AC-004.7 AC-004.8 stale outcomes cannot overwrite a newer claim")
    void guardsOutcomes() throws Exception {
        var dataSource = jobs();
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new EmailOutboxRepository(dataSource);
        var tenants = new MockWorkerTenantService(Set.of(WORKSPACE), Set.of());
        UUID id = queue(repository, tenants, "Lease");
        var first = new AtomicReference<EmailOutboxRepository.Row>();
        repository.withDueRows(NOW, 20, rows -> {
            var row = rows.get(0);
            repository.claim(row, NOW, NOW.plusSeconds(300));
            first.set(row.claimed(NOW));
        });
        repository.withDueRows(
                NOW.plusSeconds(300),
                20,
                rows -> repository.claim(rows.get(0), NOW.plusSeconds(300), NOW.plusSeconds(600)));
        repository.finish(first.get(), "SENT", NOW.plusSeconds(301));
        repository.reschedule(first.get(), NOW.plusSeconds(500));
        assertThat(jdbc.queryForMap("select * from email_outbox where email_outbox_id = ?", id))
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 2);
        assertThat(jdbc.queryForObject(
                                "select next_attempt_at from email_outbox where email_outbox_id = ?",
                                Timestamp.class,
                                id)
                        .toInstant())
                .isEqualTo(NOW.plusSeconds(600));
    }

    static class MutableClock extends Clock {
        Instant now = NOW;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }
}
