package vn.tsolve.platform.config;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.platform.service.impl.ObjectFileStorage;

// ponytail: storage is optional while FILE_STORAGE_* are not in every .env and test setup (D9); AC-008.5 makes them
// required — then drop the condition and the empty defaults.
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${tsolve.storage.endpoint:}'.isBlank()")
@EnableConfigurationProperties(StorageConfig.StorageProperties.class)
public class StorageConfig {

    // Must match Garage's s3_region so its signature verification accepts our requests.
    public static final Region REGION = Region.of("garage");

    @Bean
    S3Client s3Client(StorageProperties properties) {
        return newS3Client(properties);
    }

    public static S3Client newS3Client(StorageProperties properties) {
        // Garage v2.5.0 rejects the SDK's default checksum trailer ('Invalid payload signature', ObjectFileStorageIT;
        // decision D12).
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    ObjectFileStorage objectFileStorage(
            S3Client s3,
            StorageProperties properties,
            ObjectProvider<TenantContext> tenantContext,
            ObjectProvider<WorkspaceCatalog> workspaceCatalog) {
        return new ObjectFileStorage(s3, properties, tenantContext, workspaceCatalog);
    }

    // Not a no-op: @ConfigurationPropertiesScan registers this record on its own, so without the condition it binds,
    // and fails, when no storage is configured.
    @ConfigurationProperties("tsolve.storage")
    @ConditionalOnExpression("!'${tsolve.storage.endpoint:}'.isBlank()")
    public record StorageProperties(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucket,
            String publicUrlPattern,
            Long urlTtlSeconds,
            Long maxBytes) {

        private static final int MAX_URL_TTL_SECONDS = 604800;
        private static final int MAX_BYTES_LIMIT = 100 * 1024 * 1024;

        // Boxed, defaulted here: `.env` may hold FILE_STORAGE_URL_TTL_SECONDS= (empty), which binds null, and
        // @DefaultValue cannot put null into a primitive.
        public StorageProperties {
            urlTtlSeconds = urlTtlSeconds == null ? 300L : urlTtlSeconds;
            maxBytes = maxBytes == null ? 10485760L : maxBytes;
            requireText(endpoint, "FILE_STORAGE_ENDPOINT");
            requireText(accessKey, "FILE_STORAGE_ACCESS_KEY");
            requireText(secretKey, "FILE_STORAGE_SECRET_KEY");
            requireText(bucket, "FILE_STORAGE_BUCKET");
            requireText(publicUrlPattern, "FILE_STORAGE_PUBLIC_URL_PATTERN");
            if (!publicUrlPattern.contains("{site}")) {
                throw new IllegalArgumentException("FILE_STORAGE_PUBLIC_URL_PATTERN must contain {site}");
            }
            if (urlTtlSeconds < 1 || urlTtlSeconds > MAX_URL_TTL_SECONDS) {
                throw new IllegalArgumentException(
                        "FILE_STORAGE_URL_TTL_SECONDS must be between 1 and 604800 (the SigV4 limit of 7 days)");
            }
            if (maxBytes < 1 || maxBytes > MAX_BYTES_LIMIT) {
                throw new IllegalArgumentException("FILE_STORAGE_MAX_BYTES must be between 1 and 104857600 (100 MB)");
            }
        }

        @Override
        public String toString() {
            return "StorageProperties[urlTtlSeconds=" + urlTtlSeconds + ", maxBytes=" + maxBytes + "]";
        }

        private static void requireText(String value, String variable) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException(variable + " must be set");
            }
        }
    }
}
