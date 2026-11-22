package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
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
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiRestoreResult;
import vn.tsolve.wiki.mapper.WikiDocumentMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.TextExtractor;
import vn.tsolve.wiki.service.WikiTrashService;
import vn.tsolve.wiki.service.WikiUploadService;
import vn.tsolve.wiki.service.impl.WikiDocumentAccessImpl;
import vn.tsolve.wiki.service.impl.WikiSourceRetrievalImpl;
import vn.tsolve.wiki.service.impl.WikiSourcesImpl;
import vn.tsolve.wiki.service.impl.WikiTrashServiceImpl;
import vn.tsolve.wiki.service.impl.WikiUploadServiceImpl;
import vn.tsolve.worker.service.TenantJobService;

class WikiTrashIT extends WikiReadTestSupport {
    private static AnnotationConfigApplicationContext context;
    private WikiTrashService service;
    private WikiDocumentRepository documents;
    private FileStorage storage;
    private TenantJobService jobs;
    private AuditService audit;

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
        service = context.getBean(WikiTrashService.class);
        documents = context.getBean("trashDocuments", WikiDocumentRepository.class);
        storage = context.getBean(FileStorage.class);
        jobs = context.getBean(TenantJobService.class);
        audit = context.getBean(AuditService.class);
        reset(documents, storage, jobs, audit);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** The repository mock delegates to the real one; an interface mock has no real method to call (BUG-286). */
    private static Object callReal(org.mockito.invocation.InvocationOnMock call) throws Exception {
        var real = context.getBean("wikiDocumentRepository", WikiDocumentRepository.class);
        try {
            return call.getMethod().invoke(real, call.getArguments());
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause() instanceof Exception cause ? cause : failure;
        }
    }

    @Test
    void hidesTrashFromEveryReadAndRetrievalExceptManagerDetail_AC_005_1_AC_005_2_AC_005_4_AC_005_7() {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "INDEXED", "Guide content");
        chunk(id, 0, 1, 0);
        var access = context.getBean(AccessRepository.class);
        var sources = new WikiSourcesImpl(access);
        var suggestions = new WikiSourceRetrievalImpl(access);
        var availability = new WikiDocumentAccessImpl(access);
        TSolveUser principal = TSolveUser.current().orElseThrow();
        assertThat(sources.nearest(principal, vector(1, 0), 0.5, 10)).hasSize(1);
        assertThat(suggestions.nearest(vector(1, 0), 0.5, 10)).hasSize(1);

        service.trash(user, id);
        var row = jdbc.queryForMap("SELECT * FROM wiki_document WHERE wiki_document_id = ?", id);
        assertThat(row).containsEntry("status", "DELETED").containsEntry("deleted_by", user);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(browse.listDocuments(user, spaceId, null)).isEmpty();
        assertThat(browse.listCompanyWide(user)).isEmpty();
        assertThat(repository.search(user, "Guide", null, 0.5)).isEmpty();
        assertThat(repository.search(user, "Guide", java.util.Arrays.toString(vector(1, 0)), 0.5))
                .isEmpty();
        assertThat(sources.nearest(principal, vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(sources.unavailable(principal, Set.of(id))).containsExactly(id);
        assertThat(suggestions.nearest(vector(1, 0), 0.5, 10)).isEmpty();
        assertThat(availability.available(user.toString(), List.of(id.toString())))
                .isEmpty();
        assertMissing(() -> context.getBean(WikiUploadService.class).download(user, id));
        assertThat(browse.getDocument(user, id).canDelete()).isFalse();
        assertThat(browse.getDocument(user, id).inTrash()).isTrue();
        assertThat(browse.getDocument(user, id).content()).isEqualTo("Guide content");
        assertThat(chunkCount(id)).isEqualTo(1);
        assertThat(service.restore(user, id).renamed()).isFalse();
        assertThat(sources.nearest(principal, vector(1, 0), 0.5, 10)).hasSize(1);
        assertThat(suggestions.nearest(vector(1, 0), 0.5, 10)).hasSize(1);
        assertThat(availability.available(user.toString(), List.of(id.toString())))
                .containsExactly(id.toString());
        assertThat(browse.getDocument(user, id).canDelete()).isTrue();
        assertThat(jdbc.queryForMap("SELECT deleted_by, deleted_at FROM wiki_document WHERE wiki_document_id = ?", id))
                .containsEntry("deleted_by", null)
                .containsEntry("deleted_at", null);
        verifyNoInteractions(storage, jobs);
    }

    @Test
    void listsNewestFirstWithFullNamesNullableFolderAndSpaceScope_AC_005_3() {
        UUID root = document(space, null, "Root", "DEPARTMENT", "INDEXED", "Text");
        UUID folder = folder(space, null, "Guides", 1);
        UUID nested = document(space, folder, "Nested", "DEPARTMENT", "INDEXED", "Text");
        UUID owner = jdbc.queryForObject("""
                INSERT INTO app_user(email, full_name, department_id, status, created_at)
                VALUES ('owner@example.test', 'Original Owner', ?, 'ACTIVE', now()) RETURNING id
                """, UUID.class, department);
        jdbc.update("UPDATE wiki_document SET owner_id = ? WHERE wiki_document_id IN (?, ?)", owner, root, nested);
        service.trash(user, root);
        service.trash(user, nested);
        jdbc.update(
                "UPDATE wiki_document SET deleted_at = ? WHERE wiki_document_id = ?",
                java.sql.Timestamp.from(UPDATED),
                root);
        document(space, null, "Published", "DEPARTMENT", "INDEXED", "Text");
        long other = space(department("Other"), "Other Wiki");
        UUID foreign = document(other, null, "Foreign", "DEPARTMENT", "INDEXED", "Text");
        jdbc.update(
                "UPDATE wiki_document SET status = 'DELETED', deleted_by = ?, deleted_at = now() WHERE wiki_document_id = ?",
                user,
                foreign);
        var trash = service.listTrash(user, spaceId);
        assertThat(trash).extracting(item -> item.documentId()).containsExactly(nested, root);
        assertThat(trash.getFirst().folderName()).isEqualTo("Guides");
        assertThat(trash.getLast().folderName()).isNull();
        assertThat(trash).allSatisfy(item -> {
            assertThat(item.owner()).isEqualTo("Original Owner");
            assertThat(item.deletedBy()).isEqualTo("Reader Name");
            assertThat(item.deletedAt()).isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoresCaseInsensitiveSuffixAndBoundsTitleInOriginalFolder_AC_005_5_AC_005_6_BUG_304(boolean longTitle) {
        UUID folder = folder(space, null, "Guides", 1);
        String base = longTitle ? "x".repeat(200) : "Guide";
        UUID id = document(space, folder, base, "DEPARTMENT", "INDEXED", "Text");
        service.trash(user, id);
        document(space, folder, base.toUpperCase(java.util.Locale.ROOT), "DEPARTMENT", "INDEXED", "Text");
        String restored = base.substring(0, Math.min(base.length(), 189)) + " (restored)";
        document(space, folder, restored.toUpperCase(java.util.Locale.ROOT), "DEPARTMENT", "INDEXED", "Text");
        String expected = base.substring(0, Math.min(base.length(), 187)) + " (restored 2)";
        var result = service.restore(user, id);
        assertThat(result).isEqualTo(new WikiRestoreResult(expected, true));
        assertThat(result.title().length()).isLessThanOrEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "SELECT folder_id FROM wiki_document WHERE wiki_document_id = ?", UUID.class, id))
                .isEqualTo(folder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED"})
    void enqueuesUnindexedRestoreOnlyOnceDatabaseHasCommitted_AC_005_7(String index) {
        UUID id = document(space, null, "Guide", "DEPARTMENT", index, "Text");
        service.trash(user, id);
        doAnswer(call -> {
                    assertThat(committedValue("SELECT status FROM wiki_document WHERE wiki_document_id = ?", id))
                            .isEqualTo("PUBLISHED");
                    assertThat(committedValue("SELECT count(*)::text FROM audit_log_entry WHERE target_id = ?", id))
                            .isEqualTo("2");
                    return null;
                })
                .when(jobs)
                .enqueueFromRequest("wiki-index", id);
        service.restore(user, id);
        verify(jobs).enqueueFromRequest("wiki-index", id);
    }

    @Test
    void cascadesPassagesAndDeletesFileAfterCommitEvenWhenStorageFails_AC_005_8_AC_005_12() {
        UUID id = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "Text");
        chunk(id, 0, 1, 0);
        service.trash(user, id);
        doAnswer(call -> {
                    assertThat(committedValue(
                                    "SELECT count(*)::text FROM wiki_document WHERE wiki_document_id = ?", id))
                            .isEqualTo("0");
                    assertThat(committedValue("SELECT count(*)::text FROM wiki_chunk WHERE wiki_document_id = ?", id))
                            .isEqualTo("0");
                    assertThat(committedValue("SELECT count(*)::text FROM audit_log_entry WHERE target_id = ?", id))
                            .isEqualTo("2");
                    throw new IllegalStateException("storage unavailable");
                })
                .when(storage)
                .delete("wiki/file");
        service.deletePermanently(user, id);
        verify(storage).delete("wiki/file");
        assertThat(documentCount(id)).isZero();
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void wrongStateAndUnknownActionsLeaveRowsAndAuditUntouched_AC_005_9() {
        UUID id = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "Text");
        assertMissing(() -> service.restore(user, id));
        assertMissing(() -> service.deletePermanently(user, id));
        service.trash(user, id);
        assertMissing(() -> service.trash(user, id));
        UUID missing = UUID.randomUUID();
        assertMissing(() -> service.trash(user, missing));
        assertMissing(() -> service.restore(user, missing));
        assertMissing(() -> service.deletePermanently(user, missing));
        assertThat(status(id)).isEqualTo("DELETED");
        assertThat(auditCount(id)).isEqualTo(1);
        verifyNoInteractions(storage, jobs);
    }

    @Test
    void rechecksManagementAndHidesTrashFromOtherUsers_AC_005_4_AC_005_10() {
        UUID published = document(space, null, "Published", "DEPARTMENT", "INDEXED", "Text");
        UUID deleted = document(space, null, "Trash", "DEPARTMENT", "INDEXED", "Text");
        service.trash(user, deleted);
        jdbc.update("DELETE FROM space_manager WHERE user_id = ?", user);
        assertThatThrownBy(() -> service.trash(user, published)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.restore(user, published)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deletePermanently(user, published)).isInstanceOf(AccessDeniedException.class);
        assertMissing(() -> browse.getDocument(user, deleted));
        assertMissing(() -> service.trash(user, deleted));
        assertMissing(() -> service.restore(user, deleted));
        assertMissing(() -> service.deletePermanently(user, deleted));
        assertThatThrownBy(() -> service.listTrash(user, spaceId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE app_user SET department_id = NULL WHERE id = ?", user);
        assertMissing(() -> service.trash(user, published));
        assertThat(status(published)).isEqualTo("PUBLISHED");
        assertThat(auditCount(published)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRASH", "RESTORE", "PERMANENT"})
    void rollsBackStatePassagesAndExternalCallbacksWhenAuditFails_AC_005_11(String operation) {
        UUID id = document(space, null, "Guide", "DEPARTMENT", "PENDING", "Text");
        chunk(id, 0, 1, 0);
        if (!operation.equals("TRASH")) service.trash(user, id);
        int before = auditCount(id);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any(), any(), any(), any());
        assertThatThrownBy(() -> {
                    switch (operation) {
                        case "TRASH" -> service.trash(user, id);
                        case "RESTORE" -> service.restore(user, id);
                        case "PERMANENT" -> service.deletePermanently(user, id);
                        default -> throw new IllegalArgumentException(operation);
                    }
                })
                .isInstanceOf(IllegalStateException.class);
        assertThat(status(id)).isEqualTo(operation.equals("TRASH") ? "PUBLISHED" : "DELETED");
        assertThat(chunkCount(id)).isEqualTo(1);
        assertThat(auditCount(id)).isEqualTo(before);
        verifyNoInteractions(storage, jobs);
    }

    @Test
    void recordsExactlyOneTransactionalAuditPerSuccessfulAction_AC_005_11() throws Exception {
        UUID id = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "Text");
        service.trash(user, id);
        service.restore(user, id);
        service.trash(user, id);
        service.deletePermanently(user, id);
        var entries = jdbc.queryForList("SELECT * FROM audit_log_entry WHERE target_id = ? ORDER BY created_at", id);
        assertThat(entries)
                .extracting(entry -> entry.get("action"))
                .containsExactly("DELETE", "PUBLISH", "DELETE", "DELETE");
        var operations = List.of("TRASH", "RESTORE", "TRASH", "PERMANENT");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            assertThat(entry).containsEntry("actor_id", user).containsEntry("target_type", "WIKI_DOCUMENT");
            var details = new ObjectMapper().readTree(entry.get("details").toString());
            assertThat(details.size()).isEqualTo(3);
            assertThat(details.get("operation").asText()).isEqualTo(operations.get(i));
            assertThat(details.get("title").asText()).isEqualTo("Guide");
            assertThat(details.get("spaceId").asText()).isEqualTo(spaceId.toString());
        }
    }

    @Test
    void concurrentRestoresKeepLoserInTrashAndAnswerTitleConflict_AC_005_17() throws Exception {
        UUID first = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "Text");
        service.trash(user, first);
        UUID second = document(space, null, "Guide", "DEPARTMENT", "INDEXED", "Text");
        service.trash(user, second);
        CyclicBarrier probed = new CyclicBarrier(2);
        doAnswer(call -> {
                    boolean taken = (boolean) callReal(call);
                    if (!taken) probed.await(10, TimeUnit.SECONDS);
                    return taken;
                })
                .when(documents)
                .existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(anyLong(), any(), any(), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> concurrentRestore(first));
            var b = executor.submit(() -> concurrentRestore(second));
            var outcomes = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(WikiRestoreResult.class::isInstance))
                    .hasSize(1);
            assertThat(outcomes.stream().filter(BusinessRuleException.class::isInstance))
                    .singleElement()
                    .satisfies(result -> assertThat(((BusinessRuleException) result).getCode())
                            .isEqualTo("MSG84"));
        }
        assertThat(jdbc.queryForList("SELECT status FROM wiki_document", String.class))
                .containsExactlyInAnyOrder("PUBLISHED", "DELETED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log_entry WHERE actor_id = ? AND details->>'operation' = 'RESTORE'",
                        Integer.class,
                        user))
                .isEqualTo(1);
    }

    @Test
    void uploadTakingProbedTitleRollsBackRestoreWithMsg84_AC_005_17() {
        UUID id = document(space, null, "Guide", "DEPARTMENT", "PENDING", "Text");
        service.trash(user, id);
        doAnswer(call -> {
                    boolean taken = (boolean) callReal(call);
                    if (!taken) {
                        try (var executor = Executors.newSingleThreadExecutor()) {
                            executor.submit(() -> document(space, null, "GUIDE", "DEPARTMENT", "PENDING", "Upload"))
                                    .get(10, TimeUnit.SECONDS);
                        }
                    }
                    return taken;
                })
                .when(documents)
                .existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(anyLong(), any(), any(), any());
        assertThatThrownBy(() -> service.restore(user, id))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG84"));
        assertThat(status(id)).isEqualTo("DELETED");
        assertThat(auditCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM wiki_document WHERE status = 'PUBLISHED' AND title = 'GUIDE'",
                        Integer.class))
                .isEqualTo(1);
        verifyNoInteractions(jobs, storage);
    }

    private String committedValue(String sql, UUID id) throws SQLException {
        // A fresh connection proves another request can see the commit before the callback runs.
        try (var connection = jdbc.getDataSource().getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private Object concurrentRestore(UUID id) {
        signIn();
        try {
            return service.restore(user, id);
        } catch (BusinessRuleException conflict) {
            return conflict;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void signIn() {
        var principal = new TSolveUser(user, "reader@example.test", "Reader Name", Set.of(Role.KNOWLEDGE_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM wiki_document WHERE wiki_document_id = ?", String.class, id);
    }

    private int documentCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM wiki_document WHERE wiki_document_id = ?", Integer.class, id);
    }

    private int chunkCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM wiki_chunk WHERE wiki_document_id = ?", Integer.class, id);
    }

    private int auditCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, id);
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                .isEqualTo("MSG110"));
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = WikiDocumentRepository.class)
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
            return spy(new AuditServiceImpl(jdbc, new ObjectMapper()));
        }

        @Bean
        FileStorage storage() {
            return mock(FileStorage.class);
        }

        @Bean
        TenantJobService jobs() {
            return mock(TenantJobService.class);
        }

        @Bean
        WikiDocumentRepository trashDocuments(@Qualifier("wikiDocumentRepository") WikiDocumentRepository repository) {
            // BUG-286: spy() cannot wrap the Spring Data JDK proxy; a delegating mock can.
            return mock(WikiDocumentRepository.class, AdditionalAnswers.delegatesTo(repository));
        }

        @Bean
        WikiTrashService trashService(
                @Qualifier("trashDocuments") WikiDocumentRepository documents,
                AccessRepository access,
                ObjectProvider<FileStorage> storage,
                AuditService audit,
                ObjectProvider<TenantJobService> jobs) {
            return new WikiTrashServiceImpl(
                    documents,
                    access,
                    new vn.tsolve.wiki.repository.WikiSearchRepository(
                            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)),
                    storage,
                    audit,
                    jobs);
        }

        @Bean
        WikiUploadService uploads(
                @Qualifier("wikiDocumentRepository") WikiDocumentRepository documents,
                FolderRepository folders,
                AccessRepository access,
                ObjectProvider<FileStorage> storage,
                AuditService audit,
                ObjectProvider<TenantJobService> jobs,
                PlatformTransactionManager transactions,
                ObjectProvider<TenantContext> tenants) {
            return new WikiUploadServiceImpl(
                    documents,
                    folders,
                    access,
                    new vn.tsolve.wiki.repository.WikiSearchRepository(
                            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)),
                    new TextExtractor(),
                    storage,
                    audit,
                    jobs,
                    transactions,
                    tenants,
                    Mappers.getMapper(WikiDocumentMapper.class),
                    new ObjectMapper());
        }
    }
}
