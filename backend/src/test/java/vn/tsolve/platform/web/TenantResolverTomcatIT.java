package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.config.TenantFilterConfig;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/**
 * The paths of the web resolver as a real Tomcat decodes them, which MockMvc cannot show: only Tomcat and the
 * production filter registration run.
 */
@SpringBootTest(
        classes = TenantResolverTomcatIT.SiteServer.class,
        webEnvironment = RANDOM_PORT,
        properties = "TSOLVE_SITE_ORIGIN_TEMPLATE=https://{slug}.tsolve.test")
class TenantResolverTomcatIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/jira%2Dapp/x", "/api/v1/jira-app;x/y"})
    @DisplayName("AC-005.8 edge 90 on a Workspace site an encoded or parameterised Jira App path answers 404 MSG06")
    void refusesAJiraAppPathOnASiteHost_AC_005_8(String rawPath) throws IOException {
        String answer = exchange("acme.tsolve.test", rawPath);

        assertThat(answer).startsWith("HTTP/1.1 404");
        String body = answer.substring(answer.indexOf("\r\n\r\n") + 4);
        assertThat(body).startsWith("{");
        assertThat(mapper.readTree(body).path("code").asText()).isEqualTo("MSG06");
    }

    /** A raw socket sends the path exactly as written and lets the test choose {@code Host}. */
    private String exchange(String host, String rawPath) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            // HTTP/1.0, so the body is not chunked and the server closes the connection
            socket.getOutputStream()
                    .write(("GET " + rawPath + " HTTP/1.0\r\nHost: " + host + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Not @TestConfiguration: with only test components listed, Boot would also load the whole application.
    @Configuration(proxyBeanMethods = false)
    // The DispatcherServlet is needed: with no servlet for a path, Tomcat answers 404 before any filter runs.
    // The placeholder configurer is needed: Tomcat builds the filter before @Value would otherwise resolve.
    @ImportAutoConfiguration({
        PropertyPlaceholderAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        JacksonAutoConfiguration.class
    })
    @Import(TenantFilterConfig.class)
    static class SiteServer {

        @Bean
        ThreadLocalTenantContext tenantContext() {
            return new ThreadLocalTenantContext(id -> true);
        }

        @Bean
        WorkspaceCatalog workspaceCatalog() {
            WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
            when(catalog.findBySiteName("acme"))
                    .thenReturn(Optional.of(new WorkspaceSummary(
                            UUID.randomUUID(),
                            "acme",
                            WorkspaceStatus.ACTIVE,
                            "acme.test",
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())));
            return catalog;
        }
    }
}
