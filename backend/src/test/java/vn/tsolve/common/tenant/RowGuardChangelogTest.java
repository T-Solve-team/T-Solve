package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.database.core.PostgresDatabase;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RowGuardChangelogTest {
    @Test
    void installsPoliciesAfterALaterModulesTable_AC_001_29_BUG_128() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            var changelog = ChangeLogParserFactory.getInstance()
                    .getParser("db/changelog/platform-row-guard-ordering.xml", resources)
                    .parse("db/changelog/platform-row-guard-ordering.xml", new ChangeLogParameters(), resources);
            var changes = changelog.getChangeSets();

            assertThat(changes).extracting(ChangeSet::getId).contains("later-module-table");
            ChangeSet last = changes.getLast();
            assertThat(last.getId()).isEqualTo("012-platform-tenant-guard-policies");
            assertThat(last.isAlwaysRun()).isTrue();
            assertThat(last.getRunOrder()).isEqualTo("last");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"011-platform-tenant-guard-function", "012-platform-tenant-guard-policies"})
    void loadsAndSendsEachPostgresBlockAsOneStatement_AC_001_29(String changesetId) throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            var changelog = ChangeLogParserFactory.getInstance()
                    .getParser("db/changelog/db.changelog-master.xml", resources)
                    .parse("db/changelog/db.changelog-master.xml", new ChangeLogParameters(), resources);
            var changeset = changelog.getChangeSets().stream()
                    .filter(change -> changesetId.equals(change.getId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(changeset.getChanges()).hasSize(1);
            assertThat(changeset.getChanges().getFirst().generateStatements(new PostgresDatabase()))
                    .hasSize(1);
        }
    }
}
