package vn.tsolve.worker;

import org.testcontainers.containers.PostgreSQLContainer;

/** Shared PostgreSQL Jobs fixture; Testcontainers cleans it up when the JVM exits. */
public final class WorkerJobsDatabase {
    private WorkerJobsDatabase() {}

    public static PostgreSQLContainer<?> instance() {
        return Holder.DB;
    }

    private static final class Holder {
        private static final PostgreSQLContainer<?> DB = start();

        private static PostgreSQLContainer<?> start() {
            var database = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("jobs");
            database.start();
            return database;
        }
    }
}
