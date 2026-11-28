package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Task 10a (KAN-258): a request-long binding skips the existence check, so one class alone may create it. */
class RequestBindingSourceScanTest {

    private static final Pattern CALL = Pattern.compile("\\bbindRequest\\s*\\(|::\\s*bindRequest\\b");

    /** The holder that defines the method; {@code TenantBinding} takes the definition over in Task 6. */
    private static final Set<String> DEFINITIONS = Set.of("ThreadLocalTenantContext.java", "TenantBinding.java");

    @Test
    @DisplayName("AC-002.2 only the web Tenant Resolver calls bindRequest")
    void onlyTheResolverCallsBindRequest_AC_002_2() throws IOException {
        Path sources = Path.of("src/main/java");
        List<String> callers;
        try (Stream<Path> files = Files.walk(sources)) {
            callers = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !DEFINITIONS.contains(file.getFileName().toString()))
                    .filter(RequestBindingSourceScanTest::callsBindRequest)
                    .map(file -> sources.relativize(file).toString().replace('\\', '/'))
                    .toList();
        }

        assertThat(callers)
                .as("files that call bindRequest")
                .containsExactly("vn/tsolve/platform/web/TenantResolverFilter.java");
    }

    private static boolean callsBindRequest(Path file) {
        try {
            return CALL.matcher(Files.readString(file)).find();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
