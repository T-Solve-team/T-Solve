package vn.tsolve.worker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.JobActivator;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.springframework.context.SmartLifecycle;

/** Owns a private Jobs pool so Spring cannot select it as the business DataSource. */
public class JobRunrRuntime implements SmartLifecycle, AutoCloseable {
    private final HikariDataSource pool;
    private final StorageProvider storage;
    private final JobRequestScheduler scheduler;
    private final BackgroundJobServer server;

    public JobRunrRuntime(WorkerProperties properties, JobActivator activator, boolean processJobs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("tsolve-jobs");
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.workerCount() + 2);
        config.setMinimumIdle(0);
        pool = new HikariDataSource(config);
        StorageProvider initializedStorage = null;
        try {
            var json = new JacksonJsonMapper();
            initializedStorage = new PostgresStorageProvider(pool);
            initializedStorage.setJobMapper(new JobMapper(json));
            storage = initializedStorage;
            scheduler = new JobRequestScheduler(storage);
            server = processJobs
                    ? new BackgroundJobServer(
                            storage,
                            json,
                            activator,
                            BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
                                    .andWorkerCount(properties.workerCount()))
                    : null;
        } catch (RuntimeException | Error failure) {
            try {
                if (initializedStorage != null) {
                    initializedStorage.close();
                }
            } finally {
                pool.close();
            }
            throw failure;
        }
    }

    public JobRequestScheduler scheduler() {
        return scheduler;
    }

    @Override
    public boolean isAutoStartup() {
        return server != null;
    }

    @Override
    public void start() {
        if (server != null) {
            server.start();
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            server.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public void close() {
        try {
            stop();
        } finally {
            try {
                storage.close();
            } finally {
                pool.close();
            }
        }
    }
}
