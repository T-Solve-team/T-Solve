package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.config.StorageConfig;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.exception.FileTooLargeException;
import vn.tsolve.platform.exception.InvalidFileKeyException;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.platform.service.impl.ObjectFileStorage;
import vn.tsolve.support.GarageTestBase;

@ExtendWith(OutputCaptureExtension.class)
class ObjectFileStorageIT extends GarageTestBase {

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int TTL_SECONDS = 5;
    private static final String KEY_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final UUID workspaceA = UUID.randomUUID();
    private final UUID workspaceB = UUID.randomUUID();
    private final FakeTenantContext tenant = new FakeTenantContext();
    private final WorkspaceCatalog catalog = new FakeWorkspaceCatalog(
            Map.of(workspaceA, summary(workspaceA, "acme"), workspaceB, summary(workspaceB, "beta")));

    private S3Client raw;
    private S3Client storageClient;
    private ObjectFileStorage storage;

    @BeforeEach
    void setUp() {
        raw = newS3Client();
        storageClient = StorageConfig.newS3Client(properties());
        storage = storage(storageClient);
    }

    @AfterEach
    void cleanUp() {
        try {
            if (raw != null) {
                for (UUID workspace : Set.of(workspaceA, workspaceB)) {
                    for (var object : raw.listObjectsV2(
                                    request -> request.bucket(bucket()).prefix(workspace + "/"))
                            .contents()) {
                        raw.deleteObject(request -> request.bucket(bucket()).key(object.key()));
                    }
                }
            }
        } finally {
            try {
                if (storageClient != null) {
                    storageClient.close();
                }
            } finally {
                if (raw != null) {
                    raw.close();
                }
            }
        }
    }

    @Test
    @DisplayName("AC-007.1 AC-007.2 AC-007.3 stores bytes under only the Workspace and a generated key")
    void storesAndReadsWorkspaceObject_AC_007_1_AC_007_2_AC_007_3() {
        byte[] content = "evidence bytes".getBytes(StandardCharsets.UTF_8);
        tenant.runIn(workspaceA, () -> {
            StoredFile stored = storage.store(new ByteArrayInputStream(content));
            assertThat(stored.key()).matches(KEY_PATTERN);
            assertThat(UUID.fromString(stored.key()).version()).isEqualTo(4);
            assertThat(stored.sizeBytes()).isEqualTo(content.length);
            assertThat(readBytes(storage, stored.key())).isEqualTo(content);
            String objectKey = workspaceA + "/" + stored.key();
            var head = raw.headObject(request -> request.bucket(bucket()).key(objectKey));
            assertThat(head.contentLength()).isEqualTo((long) content.length);
            assertThat(head.contentType()).isEqualTo("application/octet-stream");
            assertThat(raw.listObjectsV2(request -> request.bucket(bucket()).prefix(workspaceA + "/"))
                            .contents())
                    .extracting(object -> object.key())
                    .containsExactly(objectKey);
        });
    }

    @Test
    @DisplayName("AC-007.5 refuses malformed keys before any object call with 404 MSG06")
    void refusesMalformedKeysBeforeAnyObjectCall_AC_007_5(CapturedOutput output) {
        String key = UUID.randomUUID().toString();
        String[] invalid = {
            "../" + key, "A" + key.substring(1), "C:\\x", "%2e%2e/" + key, key + "\0", "a/b", "/" + key, "", null
        };
        try (S3Client dead = failingClient("http://127.0.0.1:1")) {
            ObjectFileStorage isolated = storage(dead);
            tenant.runIn(workspaceA, () -> {
                for (String candidate : invalid) {
                    assertInvalidKey(() -> isolated.read(candidate));
                    assertInvalidKey(() -> isolated.delete(candidate));
                    assertInvalidKey(() -> isolated.signedUrl(candidate));
                }
            });
        }
        var refusals = output.getAll()
                .lines()
                .filter(line -> line.contains("Rejected invalid file key"))
                .toList();
        assertThat(refusals).hasSize(invalid.length * 3);
        assertThat(refusals)
                .allSatisfy(line -> assertThat(line).doesNotContain("..", "%2e", "C:\\", "\0", "/" + key, key));
    }

    @Test
    @DisplayName("AC-007.6 accepts the size boundary and empty content, rejects overflow before upload")
    void enforcesSizeLimitWithoutPartialObjects_AC_007_6() {
        tenant.runIn(workspaceA, () -> {
            byte[] content = new byte[MAX_BYTES];
            Arrays.fill(content, (byte) 37);
            StoredFile full = storage.store(new ByteArrayInputStream(content));
            assertThat(full.sizeBytes()).isEqualTo(MAX_BYTES);
            assertThat(readBytes(storage, full.key())).isEqualTo(content);
            int before = objectCount();
            assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[MAX_BYTES + 1])))
                    .isInstanceOfSatisfying(FileTooLargeException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo("MSG28");
                        assertThat(exception.getStatus().value()).isEqualTo(413);
                    });
            assertThat(objectCount()).isEqualTo(before);
            StoredFile empty = storage.store(InputStream.nullInputStream());
            assertThat(empty.sizeBytes()).isZero();
            assertThat(readBytes(storage, empty.key())).isEmpty();
        });
        try (S3Client dead = failingClient("http://127.0.0.1:1")) {
            ObjectFileStorage isolated = storage(dead);
            tenant.runIn(workspaceA, () -> assertThatThrownBy(
                            () -> isolated.store(new ByteArrayInputStream(new byte[MAX_BYTES + 1])))
                    .isInstanceOf(FileTooLargeException.class));
        }
    }

    @Test
    @DisplayName("AC-007.7 an unbound or absent TenantContext touches neither input nor object storage")
    void refusesOperationsWithoutWorkspaceBeforeReadingInput_AC_007_7() {
        try (S3Client dead = failingClient("http://127.0.0.1:1")) {
            ObjectFileStorage absent = new ObjectFileStorage(
                    dead,
                    properties(),
                    new StaticListableBeanFactory().getBeanProvider(TenantContext.class),
                    catalogProvider());
            for (ObjectFileStorage candidate : new ObjectFileStorage[] {storage(dead), absent}) {
                InputStream unreadable = new InputStream() {
                    @Override
                    public int read() {
                        throw new AssertionError("An unbound store must not read its input");
                    }
                };
                assertNoWorkspace(() -> candidate.store(unreadable));
                assertNoWorkspace(() -> candidate.read(UUID.randomUUID().toString()));
                assertNoWorkspace(() -> candidate.delete(UUID.randomUUID().toString()));
                assertNoWorkspace(() -> candidate.signedUrl(UUID.randomUUID().toString()));
            }
        }
    }

    @Test
    @DisplayName("AC-007.8 an unreachable object store leaves no objects or multipart uploads")
    void failsAtomicallyWhenGarageIsUnreachable_AC_007_8() {
        try (S3Client dead = failingClient("http://127.0.0.1:1")) {
            ObjectFileStorage isolated = storage(dead);
            tenant.runIn(
                    workspaceA, () -> assertThatThrownBy(() -> isolated.store(new ByteArrayInputStream(new byte[1024])))
                            .isInstanceOf(SdkClientException.class));
            assertNoUploads();
        }
    }

    @Test
    @DisplayName("AC-007.8 a connection closed during the request leaves no objects or multipart uploads")
    void failsAtomicallyWhenConnectionBreaks_AC_007_8() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                var executor = Executors.newSingleThreadExecutor();
                S3Client broken = failingClient("http://127.0.0.1:" + server.getLocalPort())) {
            server.setSoTimeout(5000);
            var accepted = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    assertThat(socket.getInputStream().readNBytes(32)).hasSize(32);
                    socket.setSoLinger(true, 0);
                }
                return null;
            });
            ObjectFileStorage isolated = storage(broken);
            tenant.runIn(workspaceA, () -> assertThatThrownBy(
                            () -> isolated.store(new ByteArrayInputStream(new byte[1024 * 1024])))
                    .isInstanceOf(SdkClientException.class));
            accepted.get(6, TimeUnit.SECONDS);
            assertNoUploads();
        }
    }

    @Test
    @DisplayName("AC-007.9 AC-007.10 missing reads return MSG06 and deletes are idempotent (edge 42)")
    void readsMissingObjectsAndDeletesIdempotently_AC_007_9_AC_007_10() {
        tenant.runIn(workspaceA, () -> {
            String missing = UUID.randomUUID().toString();
            assertMissing(() -> storage.read(missing));
            assertThatCode(() -> storage.delete(missing)).doesNotThrowAnyException();
            StoredFile stored = storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}));
            storage.delete(stored.key());
            assertThat(objectCount()).isZero();
            assertThatCode(() -> storage.delete(stored.key())).doesNotThrowAnyException();
            assertMissing(() -> storage.read(stored.key()));
        });
    }

    @Test
    @DisplayName("AC-007.9 maps generic S3 404 reads to MSG06 and propagates other S3 failures")
    void mapsOnlyAMissingKeyToNotFoundAndPropagatesOtherS3Failures_AC_007_9() {
        S3Client client = mock(S3Client.class);
        ObjectFileStorage isolated = storage(client);
        String key = UUID.randomUUID().toString();
        tenant.runIn(workspaceA, () -> {
            doThrow(NoSuchKeyException.builder().statusCode(404).build())
                    .when(client)
                    .getObject(ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any());
            assertMissing(() -> isolated.read(key));
            // A generic 404 (a missing bucket) is not a missing file.
            for (int status : new int[] {404, 403, 500}) {
                S3Exception failure =
                        (S3Exception) S3Exception.builder().statusCode(status).build();
                doThrow(failure).when(client).getObject(ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any());
                assertThatThrownBy(() -> isolated.read(key)).isSameAs(failure);
            }
        });
    }

    @Test
    @DisplayName("AC-007.9 the startup check passes for the bucket and fails for a missing one")
    void checksTheBucketAtStartup_AC_007_9() {
        assertThatCode(storage::checkBucket).doesNotThrowAnyException();
        StorageConfig.StorageProperties missing = new StorageConfig.StorageProperties(
                endpoint(),
                accessKey(),
                secretKey(),
                "missing-" + UUID.randomUUID(),
                "http://{site}.localhost:" + s3Port(),
                (long) TTL_SECONDS,
                (long) MAX_BYTES);
        ObjectFileStorage absent = new ObjectFileStorage(storageClient, missing, tenantProvider(), catalogProvider());
        assertThatThrownBy(absent::checkBucket).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("AC-007.11 AC-007.4 AC-007.3 another Workspace cannot read, sign or delete the object")
    void isolatesWorkspaceObjects_AC_007_11_AC_007_4_AC_007_3() {
        byte[] content = new byte[] {4, 5, 6};
        StoredFile stored = tenant.runIn(workspaceA, () -> storage.store(new ByteArrayInputStream(content)));
        tenant.runIn(workspaceB, () -> {
            assertMissing(() -> storage.read(stored.key()));
            assertMissing(() -> storage.signedUrl(stored.key()));
            assertThatCode(() -> storage.delete(stored.key())).doesNotThrowAnyException();
        });
        assertThat(raw.headObject(request -> request.bucket(bucket()).key(workspaceA + "/" + stored.key()))
                        .contentLength())
                .isEqualTo((long) content.length);
        tenant.runIn(
                workspaceA, () -> assertThat(readBytes(storage, stored.key())).isEqualTo(content));
    }

    @Test
    @DisplayName("AC-007.12 AC-007.15 signed URLs bind the host and object, expire, and disallow unsigned reads")
    void signsOnlyTheWorkspaceObjectWithExpiry_AC_007_12_AC_007_15() throws Exception {
        byte[] content = "signed evidence\r\nwith bytes".getBytes(StandardCharsets.UTF_8);
        URI signed = tenant.runIn(workspaceA, () -> {
            StoredFile stored = storage.store(new ByteArrayInputStream(content));
            URI uri = storage.signedUrl(stored.key());
            assertThat(uri.getHost()).isEqualTo("acme.localhost");
            assertThat(uri.getPath()).isEqualTo("/" + bucket() + "/" + workspaceA + "/" + stored.key());
            assertThat(uri.getRawQuery()).contains("X-Amz-Signature=").contains("X-Amz-Expires=" + TTL_SECONDS);
            return uri;
        });
        HttpResponse downloaded = get(signed);
        assertThat(downloaded.status()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);
        String key = signed.getPath().substring(signed.getPath().lastIndexOf('/') + 1);
        URI altered =
                URI.create(signed.toString().replace(key, UUID.randomUUID().toString()));
        assertThat(get(altered).status()).isEqualTo(403);
        URI unsigned = URI.create(signed.toString().split("\\?", 2)[0]);
        assertThat(get(unsigned).status()).isEqualTo(403);
        Thread.sleep((TTL_SECONDS + 3) * 1000L);
        // Garage answers an expired presigned URL with 400.
        HttpResponse expired = get(signed);
        assertThat(expired.status()).isIn(400, 403);
        assertThat(expired.body()).isNotEqualTo(content);
        tenant.runIn(workspaceB, () -> {
            StoredFile stored = storage.store(InputStream.nullInputStream());
            assertThat(storage.signedUrl(stored.key()).getHost()).isEqualTo("beta.localhost");
        });
    }

    @Test
    @DisplayName("AC-007.13 no signed URL is issued for a missing object")
    void refusesSigningMissingObject_AC_007_13() {
        tenant.runIn(
                workspaceA,
                () -> assertMissing(() -> storage.signedUrl(UUID.randomUUID().toString())));
    }

    @Test
    @DisplayName("AC-007.14 storing and reading bytes creates no local temporary files")
    void keepsNoLocalFiles_AC_007_14(@TempDir Path tempDir) throws IOException {
        try (Recording recording = new Recording()) {
            recording.setToDisk(false);
            recording.enable("jdk.FileWrite").withThreshold(Duration.ZERO);
            recording.enable("jdk.FileForce").withThreshold(Duration.ZERO);
            recording.start();
            tenant.runIn(workspaceA, () -> {
                byte[] content = new byte[1024 * 1024];
                StoredFile stored = storage.store(new ByteArrayInputStream(content));
                assertThat(readBytes(storage, stored.key())).isEqualTo(content);
            });
            Path recordingPath = tempDir.resolve("storage-io.jfr");
            recording.dump(recordingPath);
            recording.stop();
            assertThat(RecordingFile.readAllEvents(recordingPath))
                    .filteredOn(event -> Set.of("jdk.FileWrite", "jdk.FileForce")
                            .contains(event.getEventType().getName()))
                    .filteredOn(event -> {
                        String path = event.getString("path");
                        // A null path is a console write (stdout/stderr), not a file on disk.
                        return path != null
                                && !Path.of(path)
                                        .toAbsolutePath()
                                        .normalize()
                                        .startsWith(tempDir.toAbsolutePath().normalize());
                    })
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("AC-007.12 refuses signing when the Workspace catalog is absent or has no bound Workspace")
    void requiresWorkspaceCatalogForSigning_AC_007_12() {
        tenant.runIn(workspaceA, () -> {
            StoredFile stored = storage.store(InputStream.nullInputStream());
            ObjectProvider<WorkspaceCatalog> absent =
                    new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class);
            ObjectProvider<WorkspaceCatalog> empty = new StaticListableBeanFactory(
                            Map.of("workspaceCatalog", new FakeWorkspaceCatalog(Map.of())))
                    .getBeanProvider(WorkspaceCatalog.class);
            for (ObjectProvider<WorkspaceCatalog> provider : Arrays.asList(absent, empty)) {
                ObjectFileStorage isolated =
                        new ObjectFileStorage(storageClient, properties(), tenantProvider(), provider);
                assertThatThrownBy(() -> isolated.signedUrl(stored.key())).isInstanceOf(IllegalStateException.class);
            }
        });
    }

    @Test
    @DisplayName("AC-007.12 refuses signing when the Workspace site is not a valid host label")
    void refusesInvalidWorkspaceSitesForSigning_AC_007_12() {
        tenant.runIn(workspaceA, () -> {
            StoredFile stored = storage.store(InputStream.nullInputStream());
            for (String site : new String[] {"x@evil.example/y", "Acme", "-acme", "acme-", "a.b", ""}) {
                WorkspaceCatalog invalid = new FakeWorkspaceCatalog(Map.of(workspaceA, summary(workspaceA, site)));
                ObjectProvider<WorkspaceCatalog> provider = new StaticListableBeanFactory(
                                Map.of("workspaceCatalog", invalid))
                        .getBeanProvider(WorkspaceCatalog.class);
                ObjectFileStorage isolated =
                        new ObjectFileStorage(storageClient, properties(), tenantProvider(), provider);
                assertThatThrownBy(() -> isolated.signedUrl(stored.key()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Workspace site name is not a valid host label");
            }
        });
    }

    private ObjectFileStorage storage(S3Client client) {
        return new ObjectFileStorage(client, properties(), tenantProvider(), catalogProvider());
    }

    private StorageConfig.StorageProperties properties() {
        return new StorageConfig.StorageProperties(
                endpoint(),
                accessKey(),
                secretKey(),
                bucket(),
                "http://{site}.localhost:" + s3Port(),
                (long) TTL_SECONDS,
                (long) MAX_BYTES);
    }

    private ObjectProvider<TenantContext> tenantProvider() {
        return new StaticListableBeanFactory(Map.of("tenantContext", tenant)).getBeanProvider(TenantContext.class);
    }

    private ObjectProvider<WorkspaceCatalog> catalogProvider() {
        return new StaticListableBeanFactory(Map.of("workspaceCatalog", catalog))
                .getBeanProvider(WorkspaceCatalog.class);
    }

    private static S3Client failingClient(String endpoint) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("garage"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey(), secretKey())))
                .forcePathStyle(true)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryStrategy(
                                StandardRetryStrategy.builder().maxAttempts(1).build())
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
    }

    private int objectCount() {
        return raw.listObjectsV2(request -> request.bucket(bucket()).prefix(workspaceA + "/"))
                .contents()
                .size();
    }

    private void assertNoUploads() {
        assertThat(objectCount()).isZero();
        assertThat(raw.listMultipartUploads(request -> request.bucket(bucket())).uploads())
                .isEmpty();
    }

    private static byte[] readBytes(ObjectFileStorage storage, String key) {
        try (InputStream content = storage.read(key)) {
            return content.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertInvalidKey(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(InvalidFileKeyException.class)
                .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("MSG06"));
    }

    private static void assertMissing(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo("MSG06"));
    }

    private static void assertNoWorkspace(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Workspace is bound");
    }

    private static WorkspaceSummary summary(UUID id, String site) {
        return new WorkspaceSummary(
                id,
                site,
                WorkspaceStatus.ACTIVE,
                site + ".example",
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static HttpResponse get(URI uri) throws IOException {
        // Preserve the signed Host while connecting directly to the container's published port.
        try (Socket socket = new Socket(host(), s3Port())) {
            socket.setSoTimeout(5000);
            String target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            String request =
                    "GET " + target + " HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream input = new BufferedInputStream(socket.getInputStream());
            int status = Integer.parseInt(readLine(input).split(" ", 3)[1]);
            int length = -1;
            boolean chunked = false;
            String header;
            while (!(header = readLine(input)).isEmpty()) {
                String lower = header.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    length = Integer.parseInt(
                            header.substring(header.indexOf(':') + 1).trim());
                }
                if (lower.startsWith("transfer-encoding:")) {
                    chunked = lower.contains("chunked");
                }
            }
            if (chunked) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                int chunk;
                while ((chunk = Integer.parseInt(
                                readLine(input).split(";", 2)[0].trim(), 16))
                        != 0) {
                    body.write(input.readNBytes(chunk));
                    readLine(input);
                }
                return new HttpResponse(status, body.toByteArray());
            }
            return new HttpResponse(status, length < 0 ? input.readAllBytes() : input.readNBytes(length));
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                return line.toString(StandardCharsets.US_ASCII).stripTrailing();
            }
            line.write(value);
        }
        throw new IOException("Unexpected end of HTTP response");
    }

    private record HttpResponse(int status, byte[] body) {}

    private static final class FakeTenantContext implements TenantContext {

        private final ThreadLocal<UUID> bound = new ThreadLocal<>();

        @Override
        public Optional<UUID> currentWorkspaceId() {
            return Optional.ofNullable(bound.get());
        }

        @Override
        public UUID requireWorkspaceId() {
            return currentWorkspaceId().orElseThrow(() -> new IllegalStateException("No Workspace is bound"));
        }

        @Override
        public <T> T runIn(UUID workspaceId, Supplier<T> action) {
            UUID previous = bound.get();
            bound.set(workspaceId);
            try {
                return action.get();
            } finally {
                if (previous == null) {
                    bound.remove();
                } else {
                    bound.set(previous);
                }
            }
        }

        @Override
        public void runIn(UUID workspaceId, Runnable action) {
            runIn(workspaceId, () -> {
                action.run();
                return null;
            });
        }
    }

    private record FakeWorkspaceCatalog(Map<UUID, WorkspaceSummary> workspaces) implements WorkspaceCatalog {

        @Override
        public Optional<WorkspaceSummary> findById(UUID workspaceId) {
            return Optional.ofNullable(workspaces.get(workspaceId));
        }

        @Override
        public Optional<WorkspaceSummary> findBySiteName(String siteName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkspaceSummary linkJiraSite(String siteUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claimEmail(String email) {
            throw new UnsupportedOperationException();
        }
    }
}
