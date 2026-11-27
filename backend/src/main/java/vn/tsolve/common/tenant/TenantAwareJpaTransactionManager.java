package vn.tsolve.common.tenant;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets {@code app.tenant_id} to the bound Workspace id at the start of every transaction, local to that transaction, on
 * the connection that JPA and JDBC code share.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        try {
            ConnectionHolder holder =
                    (ConnectionHolder) TransactionSynchronizationManager.getResource((DataSource) getDataSource());
            try (PreparedStatement set =
                    holder.getConnection().prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                set.setString(1, TenantBinding.require().toString());
                set.execute();
            }
        } catch (SQLException | RuntimeException failure) {
            // Spring does not clean up after a doBegin that fails past super: undo it, or the thread keeps the
            // resources.
            EntityManagerHolder entityManager =
                    (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            try {
                entityManager.getEntityManager().getTransaction().rollback();
            } finally {
                doCleanupAfterCompletion(transaction);
            }
            throw new CannotCreateTransactionException("Could not set the tenant of the transaction", failure);
        }
    }
}
