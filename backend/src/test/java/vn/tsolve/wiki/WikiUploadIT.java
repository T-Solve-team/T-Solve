package vn.tsolve.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.CodedException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiPublishMetadata;
import vn.tsolve.wiki.entity.WikiDocument.Visibility;
import vn.tsolve.wiki.mapper.WikiDocumentMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.service.TextExtractor;
import vn.tsolve.wiki.service.WikiUploadService;
import vn.tsolve.wiki.service.impl.WikiUploadServiceImpl;
import vn.tsolve.worker.service.TenantJobService;

class WikiUploadIT extends WikiReadTestSupport {
    private static AnnotationConfigApplicationContext context;
    private WikiUploadService service;
    private TestStorage storage;
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
        service = context.getBean(WikiUploadService.class);
        storage = context.getBean(TestStorage.class);
        storage.clear();
        storage.afterStore = null;
        jobs = context.getBean(TenantJobService.class);
        audit = context.getBean(AuditService.class);
        reset(jobs, audit);
        doAnswer(call -> {
                    UUID id = call.getArgument(1);
                    // A separate connection sees the row only after the transaction committed.
                    try (var connection = jdbc.getDataSource().getConnection();
                            var query = connection.prepareStatement(
                                    "SELECT count(*) FROM wiki_document WHERE wiki_document_id = ?")) {
                        query.setObject(1, id);
                        try (var result = query.executeQuery()) {
                            result.next();
                            assertThat(result.getInt(1)).isEqualTo(1);
                        }
                    }
                    return UUID.randomUUID();
                })
                .when(jobs)
                .enqueue(any(), any());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesBatchWithFolderOriginalFilesAuditAndJobsAfterCommit_AC_004_6_AC_004_12_AC_004_13_AC_002_12_BUG_271()
            throws Exception {
        UUID folder = folder(space, null, "Guides", 1);
        var files = List.of(file("guide.pdf"), file("guide.docx"));
        var result = publishBatch(
                user,
                spaceId,
                List.copyOf(files),
                new WikiPublishMetadata(
                        folder,
                        List.of(row(" Guide ", Visibility.DEPARTMENT), row("Handbook", Visibility.COMPANY_WIDE))));
        assertThat(result).extracting(WikiDocumentRef::title).containsExactly("Guide", "Handbook");
        assertThat(storage.files).hasSize(2);
        for (int i = 0; i < 2; i++) {
            UUID id = result.get(i).documentId();
            var document = jdbc.queryForMap("SELECT * FROM wiki_document WHERE wiki_document_id = ?", id);
            assertThat(document)
                    .containsEntry("folder_id", folder)
                    .containsEntry("owner_id", user)
                    .containsEntry("status", "PUBLISHED")
                    .containsEntry("index_status", "PENDING");
            assertThat(document.get("content").toString()).contains("guide with readable text.");
            assertThat(storage.files.get(document.get("file_key")))
                    .isEqualTo(files.get(i).getBytes());
            var entry = jdbc.queryForMap("SELECT * FROM audit_log_entry WHERE target_id = ?", id);
            assertThat(entry)
                    .containsEntry("action", "PUBLISH")
                    .containsEntry("target_type", "WIKI_DOCUMENT")
                    .containsEntry("actor_id", user);
            var details = new ObjectMapper().readTree(entry.get("details").toString());
            assertThat(details.size()).isEqualTo(5);
            assertThat(details.path("operation").asText()).isEqualTo("UPLOAD");
            assertThat(details.path("spaceId").asText()).isEqualTo(spaceId.toString());
            assertThat(details.path("folderId").asText()).isEqualTo(folder.toString());
            assertThat(details.path("title").asText()).isEqualTo(result.get(i).title());
            assertThat(details.path("visibility").asText()).isEqualTo(i == 0 ? "DEPARTMENT" : "COMPANY_WIDE");
            verify(jobs).enqueue("wiki-index", id);
            var download = service.download(user, id);
            var output = new ByteArrayOutputStream();
            download.content().writeTo(output);
            assertThat(output.toByteArray()).isEqualTo(files.get(i).getBytes());
        }
    }

    @Test
    void enforcesPublishedTitleUniquenessPerFolderIgnoringCase_AC_004_8() throws IOException {
        publish("Guide", null);
        assertThatThrownBy(() -> publish(" GUIDE ", null))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG84");
                    assertThat(error.getField()).isEqualTo("documents[0].title");
                });
        UUID folder = folder(space, null, "Other", 1);
        publish("Guide", folder);
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE folder_id IS NULL");
        publish("Guide", null);
        assertThat(storage.files).hasSize(3);
    }

    @Test
    void rejectsUnknownAndCrossSpaceFolders_AC_004_6_AC_004_11() {
        long otherSpace = space(department("Other"), "Other Wiki");
        UUID foreign = folder(otherSpace, null, "Foreign", 1);
        for (UUID folder : List.of(UUID.randomUUID(), foreign)) {
            assertThatThrownBy(() -> publish("Guide", folder))
                    .isInstanceOfSatisfying(CodedException.class, error -> assertThat(error.getCode())
                            .isEqualTo("MSG04"));
        }
        assertEmpty();
    }

    @Test
    void rollsBackRowsAuditAndFilesWhenSecondAuditFails_AC_004_11_AC_004_12_AC_004_13() throws IOException {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
                    call.callRealMethod();
                    if (calls.incrementAndGet() == 2) throw new IllegalStateException("Audit unavailable");
                    return null;
                })
                .when(audit)
                .record(any(), any(), any(), any());
        assertThatThrownBy(() -> publishBatch(
                        user,
                        spaceId,
                        List.of(file("guide.pdf"), file("guide.docx")),
                        new WikiPublishMetadata(
                                null, List.of(row("One", Visibility.DEPARTMENT), row("Two", Visibility.COMPANY_WIDE)))))
                .isInstanceOf(IllegalStateException.class);
        assertEmpty();
    }

    @Test
    void removesEarlierObjectsWhenStorageFails_AC_004_11_BUG_273() throws IOException {
        jdbc.update("""
                INSERT INTO audit_log_entry(actor_id, actor_email, action, target_type, target_id, created_at)
                VALUES (?, 'previous@example.test', 'PUBLISH', 'WIKI_DOCUMENT', ?, now())
                """, UUID.randomUUID(), UUID.randomUUID());
        storage.failOnStore = 2;
        assertThatThrownBy(() -> publishBatch(
                        user,
                        spaceId,
                        List.of(file("guide.pdf"), file("guide.docx")),
                        new WikiPublishMetadata(
                                null, List.of(row("One", Visibility.DEPARTMENT), row("Two", Visibility.DEPARTMENT)))))
                .isInstanceOf(IllegalStateException.class);
        assertEmpty();
    }

    @Test
    void concurrentUploadsKeepOneTitleAndRemoveLoserFile_AC_004_8_AC_004_11_AC_004_13() throws Exception {
        storage.afterStore = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentPublish("Guide"));
            var second = executor.submit(() -> concurrentPublish("GUIDE"));
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results.stream()
                            .filter(WikiDocumentRef.class::isInstance)
                            .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                            .filter(BusinessRuleException.class::isInstance)
                            .count())
                    .isEqualTo(1);
            var error = results.stream()
                    .filter(BusinessRuleException.class::isInstance)
                    .map(BusinessRuleException.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(error.getCode()).isEqualTo("MSG84");
        }
        assertThat(storage.files).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wiki_document", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_entry WHERE actor_id = ?", Integer.class, user))
                .isEqualTo(1);
        org.mockito.Mockito.verify(jobs, org.mockito.Mockito.times(1)).enqueue(any(), any());
    }

    @Test
    void downloadsFollowCurrentViewRuleAndDenyTrashEvenForManager_AC_002_12_AC_002_13_AC_004_17() throws IOException {
        UUID id = publish("Guide", null).documentId();
        jdbc.update("DELETE FROM space_manager WHERE user_id = ?", user);
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'KNOWLEDGE_MANAGER'", user);
        assertThat(service.download(user, id).fileName()).isEqualTo("guide.pdf");
        assertThatThrownBy(() -> publish("Other", null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("UPDATE app_user SET department_id = NULL WHERE id = ?", user);
        assertUnavailable(id);
        jdbc.update("UPDATE wiki_document SET visibility = 'COMPANY_WIDE' WHERE wiki_document_id = ?", id);
        assertThat(service.download(user, id).contentType()).isEqualTo("application/pdf");
        jdbc.update("UPDATE space SET status = 'HIDDEN' WHERE space_id = ?", space);
        assertUnavailable(id);
        jdbc.update("UPDATE space SET status = 'ACTIVE' WHERE space_id = ?", space);
        jdbc.update("UPDATE department SET status = 'DELETED' WHERE department_id = ?", department);
        assertUnavailable(id);
        jdbc.update("UPDATE department SET status = 'ACTIVE' WHERE department_id = ?", department);
        manager();
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", id);
        assertUnavailable(id);
        assertUnavailable(UUID.randomUUID());
    }

    private Object concurrentPublish(String title) throws IOException {
        signIn();
        try {
            return publish(title, null);
        } catch (BusinessRuleException error) {
            return error;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertUnavailable(UUID id) {
        assertThatThrownBy(() -> service.download(user, id))
                .isInstanceOfSatisfying(CodedException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG110"));
    }

    private void assertEmpty() {
        assertThat(storage.files).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wiki_document", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_entry WHERE actor_id = ?", Integer.class, user))
                .isZero();
        verifyNoInteractions(jobs);
    }

    private List<WikiDocumentRef> publishBatch(
            UUID userId, UUID publicSpaceId, List<MultipartFile> files, WikiPublishMetadata metadata)
            throws IOException {
        return service.publish(userId, publicSpaceId, files, new ObjectMapper().writeValueAsString(metadata));
    }

    private WikiDocumentRef publish(String title, UUID folder) throws IOException {
        return publishBatch(
                        user,
                        spaceId,
                        List.of(file("guide.pdf")),
                        new WikiPublishMetadata(folder, List.of(row(title, Visibility.DEPARTMENT))))
                .getFirst();
    }

    private WikiPublishMetadata.Document row(String title, Visibility visibility) {
        return new WikiPublishMetadata.Document(title, visibility);
    }

    private MockMultipartFile file(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/wiki/" + name)) {
            return new MockMultipartFile("files", name, "application/octet-stream", input.readAllBytes());
        }
    }

    private void signIn() {
        var principal = new TSolveUser(user, "reader@example.test", "Reader Name", Set.of(Role.KNOWLEDGE_MANAGER));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    static class TestStorage extends InMemoryFileStorage {
        volatile CyclicBarrier afterStore;

        @Override
        public StoredFile store(InputStream content) {
            var stored = super.store(content);
            var barrier = afterStore;
            if (barrier != null) {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }
            return stored;
        }
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
        TestStorage storage() {
            return new TestStorage();
        }

        @Bean
        TenantJobService jobs() {
            return mock(TenantJobService.class);
        }

        @Bean
        WikiSearchRepository searchRepository() {
            return new WikiSearchRepository(
                    new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        }

        @Bean
        WikiUploadService uploadService(
                WikiDocumentRepository documents,
                FolderRepository folders,
                AccessRepository access,
                WikiSearchRepository search,
                ObjectProvider<FileStorage> storage,
                AuditService audit,
                TenantJobService jobs,
                PlatformTransactionManager transactions,
                ObjectProvider<TenantContext> tenants) {
            return new WikiUploadServiceImpl(
                    documents,
                    folders,
                    access,
                    search,
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
