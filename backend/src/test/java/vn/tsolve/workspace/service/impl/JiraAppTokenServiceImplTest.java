package vn.tsolve.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.exception.JiraAppSiteNotRegisteredException;
import vn.tsolve.workspace.exception.JiraAppTokenRejectedException;
import vn.tsolve.workspace.repository.JiraAppTokenStore;

class JiraAppTokenServiceImplTest {
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CLOUD = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final String BASE = "https://api.atlassian.com/ex/jira/" + CLOUD;
    private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");

    private final JiraWorkspaceLookup lookup = mock(JiraWorkspaceLookup.class);
    private final TenantScope scope = mock(TenantScope.class);
    private final JiraAppTokenStore store = mock(JiraAppTokenStore.class);
    private JiraAppTokenServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(lookup.workspaceOf(CLOUD)).thenReturn(Optional.of(WORKSPACE));
        when(scope.runIn(any(), any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
        service =
                new JiraAppTokenServiceImpl(lookup, scope, store, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String jwt(String payload) {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        return url.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + url.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static String token(long expSeconds) {
        return jwt("{\"exp\":" + expSeconds + ",\"sub\":\"app\"}");
    }

    @Test
    void holdsTheTokenForTheWorkspaceOfTheSiteUntilItsExp_AC_014_1() {
        long exp = NOW.getEpochSecond() + 3 * 3600;
        String token = token(exp);

        service.receive(CLOUD, BASE, token);

        verify(scope).runIn(eq(WORKSPACE), any());
        verify(store).hold(WORKSPACE, new JiraAppToken(token, CLOUD, NOW, Instant.ofEpochSecond(exp)));
    }

    @Test
    void refusesASiteWithoutAWorkspace_AC_014_2() {
        when(lookup.workspaceOf(CLOUD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receive(CLOUD, BASE, token(NOW.getEpochSecond() + 60)))
                .isInstanceOf(JiraAppSiteNotRegisteredException.class);
        verifyNoInteractions(store);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-dots", "a.!!!.c", "a.e30.c"})
    void refusesAMissingOrUnreadableToken_AC_014_3(String token) {
        assertThatThrownBy(() -> service.receive(CLOUD, BASE, token)).isInstanceOf(JiraAppTokenRejectedException.class);
        verifyNoInteractions(store);
    }

    @Test
    void refusesANullOrExpiredToken_AC_014_3() {
        assertThatThrownBy(() -> service.receive(CLOUD, BASE, token(NOW.getEpochSecond())))
                .isInstanceOf(JiraAppTokenRejectedException.class);
        assertThatThrownBy(() -> service.receive(CLOUD, BASE, null)).isInstanceOf(JiraAppTokenRejectedException.class);
        verifyNoInteractions(store);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://evil.example/ex/jira/1324a887-45db-1bf4-1e99-ef0ff456d421",
                "https://api.atlassian.com/ex/jira/2324a887-45db-1bf4-1e99-ef0ff456d421",
                "http://api.atlassian.com/ex/jira/1324a887-45db-1bf4-1e99-ef0ff456d421",
                "https://api.atlassian.com/ex/jira/1324a887-45db-1bf4-1e99-ef0ff456d421/../x"
            })
    void refusesAForeignApiBase_AC_014_4(String apiBaseUrl) {
        assertThatThrownBy(() -> service.receive(CLOUD, apiBaseUrl, token(NOW.getEpochSecond() + 60)))
                .isInstanceOf(JiraAppTokenRejectedException.class);
        verifyNoInteractions(store);
    }

    @Test
    void refusesACloudIdThatIsNotACanonicalUuid_AC_014_4() {
        assertThatThrownBy(() -> service.receive(
                        "not-a-uuid", "https://api.atlassian.com/ex/jira/not-a-uuid", token(NOW.getEpochSecond() + 60)))
                .isInstanceOf(JiraAppTokenRejectedException.class);
        verifyNoInteractions(store);
    }

    @Test
    void logsOnlyTheWorkspaceAndNeverTheToken_AC_014_6() {
        String token = token(NOW.getEpochSecond() + 3600);
        Logger logger = (Logger) LoggerFactory.getLogger(JiraAppTokenServiceImpl.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            service.receive(CLOUD, BASE, token);
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(logs.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                .contains(WORKSPACE.toString())
                .doesNotContain(token));
        assertThatThrownBy(() -> service.receive(CLOUD, BASE, "x.y.z")).hasMessageNotContaining("x.y.z");
    }
}
