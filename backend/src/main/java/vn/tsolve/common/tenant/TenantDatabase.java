package vn.tsolve.common.tenant;

import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Qualified SQL resources are held together so Boot's legacy DataSource and JPA auto-configuration stay intact. */
public final class TenantDatabase {
    private final JdbcTemplate jdbc;
    private final JdbcTransactionManager transactionManager;

    public TenantDatabase(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new JdbcTransactionManager(dataSource);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public TransactionTemplate newTransaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public <T> T read(Supplier<T> query) {
        var template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template.execute(status -> query.get());
    }
}
