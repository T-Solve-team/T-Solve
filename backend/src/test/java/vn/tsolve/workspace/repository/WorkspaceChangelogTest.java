package vn.tsolve.workspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.changelog.ChangeLogParameters;
import liquibase.database.core.PostgresDatabase;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceChangelogTest {

    @Test
    @DisplayName("AC-005.1 appends the Workspace changeset with explicit rollback to the master changelog")
    void appendsWorkspaceMigrationWithRollback() throws Exception {
        try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            var changelog = new XMLChangeLogSAXParser()
                    .parse("db/changelog/db.changelog-master.xml", new ChangeLogParameters(), resources);
            var workspaceChangeSet = changelog.getChangeSets().stream()
                    .filter(changeSet -> changeSet.getId().equals("035-workspace-info"))
                    .findFirst()
                    .orElseThrow();

            assertThat(workspaceChangeSet.getAuthor()).isEqualTo("workspace");
            assertThat(workspaceChangeSet.supportsRollback(new PostgresDatabase())).isTrue();
            assertThat(workspaceChangeSet.getRollback().getChanges()).isNotEmpty();
        }
    }
}
