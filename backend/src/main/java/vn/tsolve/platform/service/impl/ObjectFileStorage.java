package vn.tsolve.platform.service.impl;

import static vn.tsolve.platform.config.StorageConfig.REGION;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.config.StorageConfig;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.exception.FileTooLargeException;
import vn.tsolve.platform.exception.InvalidFileKeyException;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.platform.service.WorkspaceCatalog;

public class ObjectFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectFileStorage.class);
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern SITE_NAME_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final S3Client s3;
    private final StorageConfig.StorageProperties properties;
    private final ObjectProvider<TenantContext> tenantContext;
    private final ObjectProvider<WorkspaceCatalog> workspaceCatalog;
    // One per Workspace site: the signed host differs per Workspace (D15), signing itself is offline.
    private final Map<String, S3Presigner> presigners = new ConcurrentHashMap<>();

    public ObjectFileStorage(
            S3Client s3,
            StorageConfig.StorageProperties properties,
            ObjectProvider<TenantContext> tenantContext,
            ObjectProvider<WorkspaceCatalog> workspaceCatalog) {
        this.s3 = s3;
        this.properties = properties;
        this.tenantContext = tenantContext;
        this.workspaceCatalog = workspaceCatalog;
    }

    /** Fails startup when the bucket is missing: a HEAD on a missing bucket is a body-less 404, like a missing key. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkBucket() {
        s3.headBucket(request -> request.bucket(properties.bucket()));
    }

    @PreDestroy
    public void closePresigners() {
        presigners.values().forEach(S3Presigner::close);
        presigners.clear();
    }

    @Override
    public StoredFile store(InputStream content) {
        UUID workspaceId = requireWorkspaceId();
        // ponytail: buffers the whole file (up to FILE_STORAGE_MAX_BYTES, at most 100 MB); callers enforce the
        // multipart limit first. Stream with a multipart upload if large files become normal.
        byte[] bytes;
        try {
            int readLimit = properties.maxBytes().intValue() + 1;
            bytes = content.readNBytes(readLimit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length > properties.maxBytes()) {
            throw new FileTooLargeException();
        }
        String key = UUID.randomUUID().toString();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(workspaceId + "/" + key)
                .contentType("application/octet-stream")
                .contentLength((long) bytes.length)
                .build();
        s3.putObject(request, RequestBody.fromBytes(bytes));
        return new StoredFile(key, bytes.length);
    }

    @Override
    public InputStream read(String key) {
        UUID workspaceId = requireWorkspaceId();
        validateKey(key);
        try {
            return s3.getObject(request -> request.bucket(properties.bucket()).key(workspaceId + "/" + key));
        } catch (S3Exception e) {
            throw mapNotFound(e);
        }
    }

    @Override
    public URI signedUrl(String key) {
        UUID workspaceId = requireWorkspaceId();
        validateKey(key);
        String objectKey = workspaceId + "/" + key;
        try {
            s3.headObject(request -> request.bucket(properties.bucket()).key(objectKey));
        } catch (S3Exception e) {
            throw mapNotFound(e);
        }
        WorkspaceCatalog catalog = workspaceCatalog.getIfAvailable();
        if (catalog == null) {
            throw new IllegalStateException("No Workspace catalog is available");
        }
        WorkspaceSummary workspace = catalog.findById(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Bound Workspace is not in the catalog"));
        String siteName = workspace.siteName();
        if (siteName == null || !SITE_NAME_PATTERN.matcher(siteName).matches()) {
            throw new IllegalStateException("Workspace site name is not a valid host label");
        }
        URI endpoint = URI.create(properties.publicUrlPattern().replace("{site}", siteName));
        S3Presigner presigner = presigners.computeIfAbsent(siteName, site -> S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build());
        try {
            GetObjectRequest get = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(properties.urlTtlSeconds()))
                    .getObjectRequest(get)
                    .build();
            return presigner.presignGetObject(request).url().toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not construct the signed object URI");
        }
    }

    @Override
    public void delete(String key) {
        UUID workspaceId = requireWorkspaceId();
        validateKey(key);
        s3.deleteObject(request -> request.bucket(properties.bucket()).key(workspaceId + "/" + key));
    }

    private UUID requireWorkspaceId() {
        TenantContext context = tenantContext.getIfAvailable();
        if (context == null) {
            throw new IllegalStateException("No Workspace is bound");
        }
        return context.requireWorkspaceId();
    }

    private RuntimeException mapNotFound(S3Exception exception) {
        if (exception instanceof NoSuchKeyException) {
            return new NotFoundException(ProblemDetails.UNEXPECTED_ERROR, Map.of());
        }
        // No object key: it names the Workspace and the file.
        log.error(
                "Object storage request failed; bucket={}, status={}, code={}",
                properties.bucket(),
                exception.statusCode(),
                exception.awsErrorDetails() == null
                        ? null
                        : exception.awsErrorDetails().errorCode());
        return exception;
    }

    private static void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            log.warn("Rejected invalid file key; length={}", key == null ? 0 : key.length());
            throw new InvalidFileKeyException();
        }
    }
}
