package vn.tsolve.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import vn.tsolve.platform.service.FileStorage;

class StorageConfigTest {

    private final String accessKey = UUID.randomUUID().toString();
    private final String secretKey = UUID.randomUUID().toString();
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(StorageConfig.class);

    @Test
    @DisplayName("AC-007.1 creates no storage when the endpoint is absent or blank")
    void createsNoStorageWithoutEndpoint_AC_007_1() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(S3Client.class).doesNotHaveBean(FileStorage.class);
        });
        contextRunner.withPropertyValues("tsolve.storage.endpoint= ").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(S3Client.class).doesNotHaveBean(FileStorage.class);
        });
    }

    @Test
    @DisplayName("AC-007.1 binds all storage settings and creates exactly one client and storage bean")
    void bindsStorageWhenEndpointIsSet_AC_007_1() {
        configuredRunner().run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(FileStorage.class)
                    .hasSingleBean(S3Client.class)
                    .hasSingleBean(StorageConfig.StorageProperties.class);
            StorageConfig.StorageProperties properties = context.getBean(StorageConfig.StorageProperties.class);
            assertThat(properties.endpoint()).isEqualTo("http://127.0.0.1:3900");
            assertThat(properties.accessKey()).isEqualTo(accessKey);
            assertThat(properties.secretKey()).isEqualTo(secretKey);
            assertThat(properties.bucket()).isEqualTo("storage-test");
            assertThat(properties.publicUrlPattern()).isEqualTo("http://{site}.localhost");
            assertThat(properties.urlTtlSeconds()).isEqualTo(60);
            assertThat(properties.maxBytes()).isEqualTo(1024);
        });
    }

    @ParameterizedTest
    @CsvSource({
        "url-ttl-seconds=604801, FILE_STORAGE_URL_TTL_SECONDS",
        "max-bytes=104857601, FILE_STORAGE_MAX_BYTES",
        "url-ttl-seconds=0, FILE_STORAGE_URL_TTL_SECONDS",
        "secret-key=, FILE_STORAGE_SECRET_KEY",
        "public-url-pattern=http://localhost, FILE_STORAGE_PUBLIC_URL_PATTERN"
    })
    @DisplayName("AC-007.12 refuses invalid settings at startup without disclosing the secret")
    void refusesInvalidStorageSettingsAtStartup_AC_007_12(String setting, String variable) {
        configuredRunner().withPropertyValues("tsolve.storage." + setting).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageNotContaining(secretKey)
                    .rootCause()
                    .hasMessageContaining(variable)
                    .hasMessageNotContaining(secretKey);
        });
    }

    private ApplicationContextRunner configuredRunner() {
        return contextRunner.withPropertyValues(
                "tsolve.storage.endpoint=http://127.0.0.1:3900",
                "tsolve.storage.access-key=" + accessKey,
                "tsolve.storage.secret-key=" + secretKey,
                "tsolve.storage.bucket=storage-test",
                "tsolve.storage.public-url-pattern=http://{site}.localhost",
                "tsolve.storage.url-ttl-seconds=60",
                "tsolve.storage.max-bytes=1024");
    }
}
