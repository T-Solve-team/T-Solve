package vn.tsolve.support;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public abstract class GarageTestBase {

    private static final String BUCKET = "tsolve-files";
    private static final String CONFIG =
            """
            metadata_dir = "/tmp/meta"
            data_dir = "/tmp/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "%s"
            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            """
                    .formatted(rpcSecret());

    // Shared for the JVM; Ryuk stops it after the test process exits.
    private static final GenericContainer<?> GARAGE = new GenericContainer<>(
                    DockerImageName.parse("dxflrs/garage:v2.5.0"))
            .withCopyToContainer(Transferable.of(CONFIG), "/etc/garage.toml")
            .withExposedPorts(3900)
            .waitingFor(Wait.forLogMessage(".*S3 API server listening.*\n", 1));

    private static final String ACCESS_KEY;
    private static final String SECRET_KEY;

    static {
        GARAGE.start();
        try {
            String nodeId =
                    execute("node", "id", "-q").getStdout().split("@", 2)[0].trim();
            execute("layout", "assign", "-z", "dc1", "-c", "1G", nodeId);
            execute("layout", "apply", "--version", "1");
            executeAfterLayout("bucket", "create", BUCKET);
            ExecResult key = executeAfterLayout("key", "create", "tsolve-test");
            String output = key.getStdout() + "\n" + key.getStderr();
            ACCESS_KEY = parse(output, "(?im)^\\s*Key ID:\\s*(GK[0-9a-f]+)\\s*$");
            SECRET_KEY = parse(output, "(?im)^\\s*Secret key:\\s*([0-9a-f]+)\\s*$");
            execute("bucket", "allow", "--read", "--write", BUCKET, "--key", "tsolve-test");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Garage bootstrap interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Garage bootstrap failed", e);
        }
    }

    public static String endpoint() {
        return "http://" + host() + ":" + s3Port();
    }

    public static String host() {
        return GARAGE.getHost();
    }

    public static int s3Port() {
        return GARAGE.getMappedPort(3900);
    }

    public static String bucket() {
        return BUCKET;
    }

    public static String accessKey() {
        return ACCESS_KEY;
    }

    public static String secretKey() {
        return SECRET_KEY;
    }

    public static S3Client newS3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.of("garage"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .forcePathStyle(true)
                .build();
    }

    private static String rpcSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static ExecResult execute(String... arguments) throws IOException, InterruptedException {
        ExecResult result = invoke(arguments);
        if (result.getExitCode() != 0) {
            throw cliFailure(result);
        }
        return result;
    }

    private static ExecResult executeAfterLayout(String... arguments) throws IOException, InterruptedException {
        ExecResult result = null;
        // The new single-node layout may not be visible immediately after apply returns.
        for (int attempt = 0; attempt < 5; attempt++) {
            result = invoke(arguments);
            if (result.getExitCode() == 0) {
                return result;
            }
            if (attempt < 4) {
                Thread.sleep(300);
            }
        }
        throw cliFailure(result);
    }

    private static ExecResult invoke(String... arguments) throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = "/garage";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return GARAGE.execInContainer(command);
    }

    private static IllegalStateException cliFailure(ExecResult result) {
        return new IllegalStateException("Garage CLI failed: " + result.getStdout() + "\n" + result.getStderr());
    }

    private static String parse(String output, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("Garage CLI did not return the expected credential labels");
        }
        return matcher.group(1);
    }
}
