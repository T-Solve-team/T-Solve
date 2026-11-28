package vn.tsolve.common.tenant;

import java.util.UUID;
import javax.sql.DataSource;

/** The connection pool of each Workspace database; {@code platform} implements it from the Registry. */
public interface TenantDataSources {

    /** Opens the pool on first use. Throws when the Workspace's database cannot be reached. */
    DataSource poolOf(UUID workspaceId);
}
