package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.mapper.WikiFolderMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.WikiFolderService;
import vn.tsolve.wiki.service.impl.WikiFolderServiceImpl;

class WikiFolderIT extends WikiReadTestSupport {
    private static AnnotationConfigApplicationContext context;
    private WikiFolderService service;

    @BeforeAll
    static void startServices() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> jdbc.getDataSource());
        context.register(Config.class);
        context.refresh();
    }

    @AfterAll
    static void closeServices() {
        if (context != null) context.close();
    }

    @BeforeEach
    void prepareManager() {
        manager();
        signIn();
        service = context.getBean(WikiFolderService.class);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsRenamesMovesAndDeletesWithTransactionalAudit_AC_003_1_AC_003_2_AC_003_8_AC_003_11() {
        WikiFolder root = create("\u2003 Root \n", null);
        WikiFolder child = create("Child", root.folderId());
        WikiFolder destination = create("Destination", null);
        UUID doc = document(space, root.folderId(), "Guide", "DEPARTMENT", "PENDING", "Text");
        WikiFolder moved =
                service.update(user, root.folderId(), new UpdateWikiFolderRequest(" Renamed ", destination.folderId()));
        assertThat(moved.name()).isEqualTo("Renamed");
        assertThat(moved.level()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT level FROM folder WHERE folder_id = ?", Integer.class, child.folderId()))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM folder WHERE folder_id = ?", UUID.class, child.folderId()))
                .isEqualTo(root.folderId());
        assertThat(jdbc.queryForObject(
                        "SELECT folder_id FROM wiki_document WHERE wiki_document_id = ?", UUID.class, doc))
                .isEqualTo(root.folderId());
        assertAudit(root.folderId(), "CREATE", "Root", null);
        assertAudit(root.folderId(), "UPDATE", "Renamed", destination.folderId());
        service.delete(user, child.folderId());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM folder WHERE folder_id = ?", Integer.class, child.folderId()))
                .isZero();
        assertAudit(child.folderId(), "DELETE", "Child", root.folderId());
        assertThat(auditCount()).isEqualTo(5);
    }

    @Test
    void recountsCommittedDocumentsAfterRestrictRollsBack_AC_003_9_AC_003_12_BUG_243() {
        WikiFolder folder = create("Upload destination", null);
        UUID documentId = document(space, folder.folderId(), "Upload", "DEPARTMENT", "PENDING", "Text");
        WikiDocumentRepository realDocuments = context.getBean(WikiDocumentRepository.class);
        WikiDocumentRepository documents = mock(WikiDocumentRepository.class);
        // Model the count observed before an upload commits, then use the real database for the fresh count.
        doReturn(0L)
                .doAnswer(call -> realDocuments.countByFolderId(folder.folderId()))
                .when(documents)
                .countByFolderId(folder.folderId());
        WikiFolderService deleting = new WikiFolderServiceImpl(
                context.getBean(FolderRepository.class),
                documents,
                context.getBean(AccessRepository.class),
                context.getBean(AuditService.class),
                Mappers.getMapper(WikiFolderMapper.class),
                context.getBean(JpaTransactionManager.class));
        int before = auditCount();
        assertThatThrownBy(() -> deleting.delete(user, folder.folderId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG87");
                    assertThat(error.getParams())
                            .containsEntry("folder_count", 0L)
                            .containsEntry("document_count", 1L);
                });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM folder WHERE folder_id = ?", Integer.class, folder.folderId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM wiki_document WHERE wiki_document_id = ?", Integer.class, documentId))
                .isEqualTo(1);
        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    void enforcesSiblingUniquenessAtRootAndUnderParent_AC_003_4() {
        WikiFolder root = create("Guides", null);
        assertConflict(() -> create(" guides ", null), "MSG85");
        WikiFolder first = create("Child", root.folderId());
        assertConflict(() -> create(" CHILD ", root.folderId()), "MSG85");
        WikiFolder other = create("Other", null);
        create("Child", other.folderId());
        assertConflict(
                () -> service.update(user, other.folderId(), new UpdateWikiFolderRequest(" GUIDES ", null)), "MSG85");
        service.update(user, first.folderId(), new UpdateWikiFolderRequest("CHILD", root.folderId()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void rejectsCyclesAndMovesThatWouldPushGrandchildToLevelFour_AC_003_5_AC_003_6() {
        WikiFolder root = create("Root", null);
        WikiFolder child = create("Child", root.folderId());
        WikiFolder grandchild = create("Grandchild", child.folderId());
        WikiFolder destination = create("Destination", null);
        assertCode(() -> create("Too deep", grandchild.folderId()), "MSG86");
        assertCode(
                () -> service.update(user, root.folderId(), new UpdateWikiFolderRequest("Root", root.folderId())),
                "MSG86");
        assertCode(
                () -> service.update(user, root.folderId(), new UpdateWikiFolderRequest("Root", grandchild.folderId())),
                "MSG86");
        assertCode(
                () -> service.update(
                        user, root.folderId(), new UpdateWikiFolderRequest("Root", destination.folderId())),
                "MSG86");
        assertThat(jdbc.queryForObject("SELECT parent_id FROM folder WHERE folder_id = ?", UUID.class, root.folderId()))
                .isNull();
        assertThat(auditCount()).isEqualTo(4);
    }

    @Test
    void countsDirectChildrenAndBothPublishedAndTrashedDocuments_AC_003_9() {
        WikiFolder root = create("Root", null);
        WikiFolder child = create("Child", root.folderId());
        create("Grandchild", child.folderId());
        document(space, root.folderId(), "Published", "DEPARTMENT", "PENDING", "Text");
        UUID trash = document(space, root.folderId(), "Trash", "DEPARTMENT", "PENDING", "Text");
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", trash);
        assertThatThrownBy(() -> service.delete(user, root.folderId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG87");
                    assertThat(error.getParams())
                            .containsEntry("folder_count", 1L)
                            .containsEntry("document_count", 2L);
                });
        WikiFolder trashOnly = create("Trash only", null);
        jdbc.update("UPDATE wiki_document SET folder_id = ? WHERE wiki_document_id = ?", trashOnly.folderId(), trash);
        assertThatThrownBy(() -> service.delete(user, trashOnly.folderId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> assertThat(error.getParams())
                        .containsEntry("folder_count", 0L)
                        .containsEntry("document_count", 1L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void rejectsUnknownFolderAndCrossSpaceParent_AC_003_7() {
        UUID foreign = folder(space(department("Other"), "Other Wiki"), null, "Foreign", 1);
        WikiFolder root = create("Root", null);
        for (UUID parent : List.of(foreign, UUID.randomUUID())) {
            assertCode(() -> create("New", parent), "MSG04");
            assertCode(
                    () -> service.update(user, root.folderId(), new UpdateWikiFolderRequest("Root", parent)), "MSG04");
        }
        assertThatThrownBy(() -> service.delete(user, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(user, UUID.randomUUID(), new UpdateWikiFolderRequest("Root", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER_MANAGER", "ROLE_REMOVED", "DEACTIVATED", "HIDDEN", "DEPARTMENT_DELETED"})
    void rechecksCurrentManagementAndScopeOnAllWrites_AC_003_7_AC_003_10(String state) {
        WikiFolder root = create("Root", null);
        switch (state) {
            case "OTHER_MANAGER" -> jdbc.update("DELETE FROM space_manager WHERE space_id = ?", space);
            case "ROLE_REMOVED" -> jdbc.update("DELETE FROM user_role WHERE user_id = ?", user);
            case "DEACTIVATED" -> jdbc.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);
            case "HIDDEN" -> jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", space);
            case "DEPARTMENT_DELETED" ->
                jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", department);
            default -> throw new IllegalArgumentException(state);
        }
        Class<? extends RuntimeException> expected = state.equals("HIDDEN") || state.equals("DEPARTMENT_DELETED")
                ? NotFoundException.class
                : AccessDeniedException.class;
        assertThatThrownBy(() -> create("New", null)).isInstanceOf(expected);
        assertThatThrownBy(() -> service.update(user, root.folderId(), new UpdateWikiFolderRequest("New", null)))
                .isInstanceOf(expected);
        assertThatThrownBy(() -> service.delete(user, root.folderId())).isInstanceOf(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder", Integer.class))
                .isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE"})
    void rollsBackFolderMutationWhenAuditFails_AC_003_11(String operation) {
        WikiFolder root = create("Root", null);
        jdbc.execute("""
                CREATE FUNCTION reject_folder_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
                """);
        jdbc.execute(
                "CREATE TRIGGER reject_folder_audit BEFORE INSERT ON audit_log_entry FOR EACH ROW EXECUTE FUNCTION reject_folder_audit()");
        try {
            assertThatThrownBy(() -> {
                        switch (operation) {
                            case "CREATE" -> create("New", null);
                            case "UPDATE" ->
                                service.update(user, root.folderId(), new UpdateWikiFolderRequest("New", null));
                            case "DELETE" -> service.delete(user, root.folderId());
                            default -> throw new IllegalArgumentException(operation);
                        }
                    })
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForList("SELECT name FROM folder", String.class))
                    .containsExactly("Root");
            assertThat(auditCount()).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER reject_folder_audit ON audit_log_entry");
            jdbc.execute("DROP FUNCTION reject_folder_audit()");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentSiblingCreatesSaveOneAndReturnOneConflict_AC_003_12(boolean nested) throws Exception {
        UUID parent = nested ? create("Parent", null).folderId() : null;
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentCreate(start, " Guides ", parent));
            var second = executor.submit(() -> concurrentCreate(start, "GUIDES", parent));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(WikiFolder.class::isInstance)).hasSize(1);
            assertThat(results.stream()
                            .filter(BusinessRuleException.class::isInstance)
                            .map(BusinessRuleException.class::cast)
                            .map(CodedException::getCode))
                    .containsExactly("MSG85");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder WHERE lower(name) = 'guides'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log_entry WHERE actor_id = ? AND details->>'name' IN ('Guides', 'GUIDES')",
                        Integer.class,
                        user))
                .isEqualTo(1);
    }

    private Object concurrentCreate(CyclicBarrier start, String name, UUID parent) throws Exception {
        signIn();
        try {
            start.await(10, TimeUnit.SECONDS);
            return create(name, parent);
        } catch (BusinessRuleException error) {
            return error;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void signIn() {
        TSolveUser principal =
                new TSolveUser(user, "reader@example.test", "Reader Name", Set.of(Role.KNOWLEDGE_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private WikiFolder create(String name, UUID parent) {
        return service.create(user, spaceId, new CreateWikiFolderRequest(name, parent));
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log_entry WHERE actor_id = ?", Integer.class, user);
    }

    private void assertAudit(UUID id, String operation, String name, UUID parent) {
        var entry = jdbc.queryForMap(
                "SELECT * FROM audit_log_entry WHERE target_id = ? AND details->>'operation' = ?", id, operation);
        assertThat(entry)
                .containsEntry("action", "FOLDER_CHANGE")
                .containsEntry("target_type", "WIKI_FOLDER")
                .containsEntry("actor_id", user);
        String details = jdbc.queryForObject(
                "SELECT details::text FROM audit_log_entry WHERE audit_log_entry_id = ?",
                String.class,
                entry.get("audit_log_entry_id"));
        try {
            var json = new ObjectMapper().readTree(details);
            assertThat(json.size()).isEqualTo(4);
            assertThat(json.get("operation").asText()).isEqualTo(operation);
            assertThat(json.get("name").asText()).isEqualTo(name);
            assertThat(json.get("spaceId").asText()).isEqualTo(spaceId.toString());
            if (parent == null) assertThat(json.get("parentId").isNull()).isTrue();
            else assertThat(json.get("parentId").asText()).isEqualTo(parent.toString());
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getField()).isEqualTo("name");
        });
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CodedException.class, error -> assertThat(error.getCode())
                .isEqualTo(code));
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = FolderRepository.class)
    static class Config {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("vn.tsolve.wiki.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        AuditService audit() {
            return new AuditServiceImpl(jdbc, new ObjectMapper());
        }

        @Bean
        WikiFolderService folderService(
                FolderRepository folders,
                WikiDocumentRepository documents,
                AccessRepository access,
                AuditService audit,
                JpaTransactionManager transactionManager) {
            return new WikiFolderServiceImpl(
                    folders, documents, access, audit, Mappers.getMapper(WikiFolderMapper.class), transactionManager);
        }
    }
}
