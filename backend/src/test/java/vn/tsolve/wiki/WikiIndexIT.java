package vn.tsolve.wiki;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.wiki.dto.WikiDocumentSummary;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiChunkRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;
import vn.tsolve.wiki.service.DoclingClient;
import vn.tsolve.wiki.service.PassageSplitter;
import vn.tsolve.wiki.service.TypedChunker;
import vn.tsolve.wiki.service.impl.WikiIndexJobHandler;

class WikiIndexIT extends WikiReadTestSupport {
    private static AnnotationConfigApplicationContext context;
    private WikiIndexJobHandler handler;
    private EmbeddingClient embeddings;

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
    void prepareHandler() {
        embeddings = context.getBean(EmbeddingClient.class);
        reset(embeddings);
        handler = context.getBean(WikiIndexJobHandler.class);
    }

    @Test
    void storesOrderedVectorsAndReplacesPriorChunksOnRedelivery_AC_004_14() {
        List<String> passages = IntStream.range(0, 17)
                .mapToObj(i -> "passage-%02d ".formatted(i) + "x".repeat(980))
                .toList();
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", String.join("\n\n", passages));
        AtomicInteger next = new AtomicInteger();
        when(embeddings.embed(anyList())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            List<String> batch = call.getArgument(0);
            return batch.stream().map(text -> vector(next.incrementAndGet(), 1)).toList();
        });

        handler.execute(id);

        assertState(id, "PUBLISHED", "INDEXED");
        var rows = jdbc.queryForList("""
                SELECT position, text, model, vector_dims(embedding) AS dimensions, created_at,
                    (embedding::real[])[1] AS first_component
                FROM wiki_chunk WHERE wiki_document_id = ? ORDER BY position
                """, id);
        assertThat(rows).hasSize(17);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i))
                    .containsEntry("position", i)
                    .containsEntry("text", "[other] Guide\n" + passages.get(i))
                    .containsEntry("model", "bge-m3")
                    .containsEntry("dimensions", 1024)
                    .containsEntry("first_component", (float) (i + 1));
            assertThat(rows.get(i).get("created_at")).isNotNull();
        }
        assertThat(repository.search(user, "unmatched", java.util.Arrays.toString(vector(1, 1)), 0.55))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(id);
        var oldIds =
                jdbc.queryForList("SELECT wiki_chunk_id FROM wiki_chunk WHERE wiki_document_id = ?", UUID.class, id);

        handler.execute(id);

        assertThat(jdbc.queryForList("SELECT wiki_chunk_id FROM wiki_chunk WHERE wiki_document_id = ?", UUID.class, id))
                .hasSize(17)
                .doesNotContainAnyElementsOf(oldIds);
        assertThat(jdbc.queryForObject(
                                "SELECT updated_at FROM wiki_document WHERE wiki_document_id = ?",
                                java.sql.Timestamp.class,
                                id)
                        .toInstant())
                .isEqualTo(UPDATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "error", "timeout", "blank", "headings"})
    void sendsOriginalFileAndIndexesTypedOrFallbackPassages_AC_007_4_AC_007_7_AC_007_8(String result) throws Exception {
        byte[] original = "original file bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var storage = new InMemoryFileStorage();
        String key = storage.store(new java.io.ByteArrayInputStream(original)).key();
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", "Stored plain text");
        jdbc.update(
                "UPDATE wiki_document SET document_type = 'GUIDE', file_key = ? WHERE wiki_document_id = ?", key, id);
        var server = new com.github.tomakehurst.wiremock.WireMockServer(0);
        server.start();
        try {
            var request = com.github.tomakehurst.wiremock.client.WireMock.post("/v1/convert/file")
                    .withMultipartRequestBody(aMultipart("files").withBody(binaryEqualTo(original)))
                    .withMultipartRequestBody(aMultipart("to_formats").withBody(equalTo("md")));
            var response = com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withHeader("Content-Type", "application/json");
            switch (result) {
                case "error" -> response.withStatus(503);
                case "timeout" -> response.withFixedDelay(2000).withBody("{}");
                case "blank" -> response.withBody("{\"document\":{\"md_content\":\" \\n \"}}");
                case "headings" -> response.withBody("{\"document\":{\"md_content\":\"# First\\n## Second\"}}");
                default -> response.withBody("{\"document\":{\"md_content\":\"# Instructions\\nConverted text\"}}");
            }
            server.stubFor(request.willReturn(response));
            String expected = result.equals("success")
                    ? "[guide] Guide > Instructions\nConverted text"
                    : "[guide] Guide\nStored plain text";
            when(embeddings.embed(List.of(expected))).thenReturn(List.of(vector(1, 0)));
            // StaticListableBeanFactory's provider rejects getObject() (BUG-354), so the handler fell back every time.
            var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
            beans.registerSingleton("storage", storage);
            var typedHandler = new WikiIndexJobHandler(
                    context.getBean(WikiDocumentRepository.class),
                    context.getBean(WikiChunkRepository.class),
                    new PassageSplitter(),
                    new TypedChunker(),
                    new DoclingClient(server.baseUrl(), result.equals("timeout") ? "1s" : "120s"),
                    beans.getBeanProvider(FileStorage.class),
                    embeddings,
                    context.getBean(PlatformTransactionManager.class),
                    "bge-m3");

            typedHandler.execute(id);

            assertState(id, "PUBLISHED", "INDEXED");
            assertThat(jdbc.queryForMap(
                            "SELECT text, model, vector_dims(embedding) AS dimensions FROM wiki_chunk WHERE wiki_document_id = ?",
                            id))
                    .containsEntry("text", expected)
                    .containsEntry("model", "bge-m3")
                    .containsEntry("dimensions", 1024);
        } finally {
            server.stop();
        }
    }

    @Test
    void lateEmbeddingFailureRetainsOldChunksAndKeywordSearch_AC_004_15() {
        UUID id = document(
                space, null, "Guide", "COMPANY_WIDE", "PENDING", ("repair " + "x".repeat(980) + "\n\n").repeat(17));
        chunk(id, 0, 1, 0);
        var old = jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id);
        when(embeddings.embed(anyList()))
                .thenAnswer(call -> {
                    List<String> batch = call.getArgument(0);
                    return batch.stream().map(text -> vector(1, 0)).toList();
                })
                .thenThrow(new AiUnavailableException());

        handler.execute(id);

        assertState(id, "PUBLISHED", "FAILED");
        assertThat(jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id))
                .isEqualTo(old);
        assertThat(repository.search(user, "repair", null, 0.55))
                .extracting(WikiDocumentSummary::documentId)
                .containsExactly(id);
        var access = context.getBean(AccessRepository.class);
        String vector = java.util.Arrays.toString(vector(1, 0));
        var read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.executeWithoutResult(status -> {
            assertThat(access.nearestVisible(user, vector, 0.55, 10)).isEmpty();
            assertThat(access.nearestCompanyWide(vector, 0.55, 10)).isEmpty();
        });
    }

    @Test
    void readsOnlyCompanyWideDocumentsForAJiraQuestion_AC_001_12_AC_007_10() {
        long other = department("Finance");
        long otherSpace = space(other, "Finance Wiki");
        UUID departmentOnly = document(space, null, "Support only", "DEPARTMENT", "INDEXED", "own");
        UUID companyWide = document(otherSpace, null, "Everyone", "COMPANY_WIDE", "INDEXED", "everyone");
        UUID foreign = document(otherSpace, null, "Finance only", "DEPARTMENT", "INDEXED", "finance");
        for (UUID id : List.of(departmentOnly, companyWide, foreign)) {
            chunk(id, 0, 1, 0);
        }
        jdbc.update("UPDATE wiki_document SET document_type = 'REFERENCE' WHERE wiki_document_id = ?", companyWide);
        var sources = new vn.tsolve.wiki.service.impl.WikiSourcesImpl(context.getBean(AccessRepository.class));
        var read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.executeWithoutResult(status -> assertThat(sources.nearestCompanyWide(vector(1, 0), 0.55, 10))
                .extracting(vn.tsolve.chatbot.source.service.WikiSources.WikiPassage::wikiDocumentId)
                .containsExactly(companyWide));
    }

    @Test
    void insertFailureRollsBackDeletionAndAllNewChunks_AC_004_14() {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", "a".repeat(1001));
        chunk(id, 0, 1, 0);
        var old = jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id);
        when(embeddings.embed(anyList())).thenReturn(List.of(vector(1, 0), vector(0, 1)));
        jdbc.execute("""
                CREATE FUNCTION fail_second_wiki_chunk() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.position = 1 THEN RAISE EXCEPTION 'test insert failure'; END IF;
                    RETURN NEW;
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_wiki_chunk BEFORE INSERT ON wiki_chunk
                FOR EACH ROW EXECUTE FUNCTION fail_second_wiki_chunk()
                """);
        try {
            assertThatThrownBy(() -> handler.execute(id)).isInstanceOf(RuntimeException.class);
            assertState(id, "PUBLISHED", "PENDING");
            assertThat(jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id))
                    .isEqualTo(old);
        } finally {
            jdbc.execute("DROP TRIGGER fail_wiki_chunk ON wiki_chunk");
            jdbc.execute("DROP FUNCTION fail_second_wiki_chunk()");
        }
    }

    @Test
    void trashedAndMissingDocumentsAreUntouched_AC_004_16() {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", "repair");
        chunk(id, 0, 1, 0);
        jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", id);
        var old = jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id);
        handler.execute(id);
        handler.execute(UUID.randomUUID());
        assertState(id, "DELETED", "PENDING");
        assertThat(jdbc.queryForList("SELECT * FROM wiki_chunk WHERE wiki_document_id = ?", id))
                .isEqualTo(old);
        verifyNoInteractions(embeddings);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void trashCommittedWhileEmbeddingPreventsEveryWrite_AC_004_16(boolean embeddingFails) {
        UUID id = document(space, null, "Guide", "COMPANY_WIDE", "PENDING", "repair");
        when(embeddings.embed(anyList())).thenAnswer(call -> {
            jdbc.update("UPDATE wiki_document SET status = 'DELETED' WHERE wiki_document_id = ?", id);
            if (embeddingFails) throw new AiUnavailableException();
            return List.of(vector(1, 0));
        });
        handler.execute(id);
        assertState(id, "DELETED", "PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wiki_chunk WHERE wiki_document_id = ?", Integer.class, id))
                .isZero();
    }

    private void assertState(UUID id, String status, String indexStatus) {
        assertThat(jdbc.queryForMap("SELECT status, index_status FROM wiki_document WHERE wiki_document_id = ?", id))
                .containsEntry("status", status)
                .containsEntry("index_status", indexStatus);
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
        EmbeddingClient embeddings() {
            return Mockito.mock(EmbeddingClient.class);
        }

        @Bean
        WikiIndexJobHandler handler(
                WikiDocumentRepository documents,
                WikiChunkRepository chunks,
                EmbeddingClient embeddings,
                PlatformTransactionManager transactions) {
            return new WikiIndexJobHandler(
                    documents,
                    chunks,
                    new PassageSplitter(),
                    new TypedChunker(),
                    new DoclingClient("", "120s"),
                    new org.springframework.beans.factory.support.StaticListableBeanFactory()
                            .getBeanProvider(FileStorage.class),
                    embeddings,
                    transactions,
                    "bge-m3");
        }
    }
}
