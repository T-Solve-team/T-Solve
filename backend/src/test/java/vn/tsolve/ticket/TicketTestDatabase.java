package vn.tsolve.ticket;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL for the ticket module's ITs, started once per JVM (backend/AGENTS.md §8: no container per IT
 * class). Use it with {@code @ImportTestcontainers(TicketTestDatabase.class)}; each IT cleans the tables it uses.
 */
public final class TicketTestDatabase {

    // started once for the JVM and stopped by Testcontainers' Ryuk
    @ServiceConnection
    public static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private TicketTestDatabase() {}
}
