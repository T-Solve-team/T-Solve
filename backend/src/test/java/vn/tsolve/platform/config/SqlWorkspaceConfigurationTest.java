package vn.tsolve.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SqlWorkspaceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SqlWorkspaceConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void enablesOnlyQualifiedSqlResources_AC_002_24_AC_002_25() {
        runner.withPropertyValues(
                        "tsolve.sql-workspace.enabled=true",
                        "tsolve.sql-workspace.base-domain=localhost",
                        "tsolve.sql-workspace.catalog.host=localhost",
                        "tsolve.sql-workspace.catalog.port=5432",
                        "tsolve.sql-workspace.catalog.database=catalog",
                        "tsolve.sql-workspace.catalog.username=catalog_app",
                        "tsolve.sql-workspace.catalog.password=test-only",
                        "tsolve.sql-workspace.pool-max=5",
                        "tsolve.sql-workspace.max-pools=2")
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasBean("sqlTenantDatabase")
                            .hasBean("tenantContext");
                    assertThat(context.getBeansOfType(javax.sql.DataSource.class))
                            .isEmpty();
                    assertThat(context.getBeansOfType(org.springframework.transaction.PlatformTransactionManager.class))
                            .isEmpty();
                });
    }

    @Test
    void disabledFeatureNeverCreatesSqlConnections_AC_002_25() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean("sqlTenantDatabase")
                .hasBean("sqlWorkspaceFilter"));
    }
}
