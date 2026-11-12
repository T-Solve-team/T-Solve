package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * AC-001.18, Q1 A: reading the saved request at the callback hands the kept {@code return_to} to the callback handler
 * through a request attribute (Spring consumes the request before the handler runs).
 */
class RedisAuthorizationRequestRepositoryReturnToTest {

    private ValueOperations<String, String> values;
    private RedisAuthorizationRequestRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void wire() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        repository = new RedisAuthorizationRequestRepository(redis);
    }

    /** Saves a request (with or without a return_to) and returns the request that sends its cookie back. */
    private MockHttpServletRequest saveAndReturn(String returnTo) {
        OAuth2AuthorizationRequest saved = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://keycloak.example/auth")
                .clientId("tsolve-web")
                .redirectUri("http://acme.localhost/api/v1/auth/callback")
                .state("state-value")
                .attributes(attributes -> {
                    if (returnTo != null) {
                        attributes.put(KeycloakAuthorizationRequestResolver.RETURN_TO, returnTo);
                    }
                })
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(saved, new MockHttpServletRequest(), response);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(anyString(), json.capture(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenReturn(json.getValue());
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        String value =
                header.substring(RedisAuthorizationRequestRepository.COOKIE_NAME.length() + 1, header.indexOf(';'));
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setCookies(new Cookie(RedisAuthorizationRequestRepository.COOKIE_NAME, value));
        return callback;
    }

    @Test
    @DisplayName("AC-001.18 removing the saved request sets the return_to request attribute")
    void removeSetsTheReturnToAttribute_AC_001_18() {
        MockHttpServletRequest callback = saveAndReturn("/tickets/42?tab=history");

        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(callback, new MockHttpServletResponse());

        assertThat(removed).isNotNull();
        assertThat(callback.getAttribute(OidcCallbackHandler.RETURN_TO_ATTRIBUTE))
                .isEqualTo("/tickets/42?tab=history");
    }

    @Test
    @DisplayName("AC-001.18 a saved request without a return_to sets no attribute")
    void removeWithoutReturnToSetsNoAttribute_AC_001_18() {
        MockHttpServletRequest callback = saveAndReturn(null);

        repository.removeAuthorizationRequest(callback, new MockHttpServletResponse());

        assertThat(callback.getAttribute(OidcCallbackHandler.RETURN_TO_ATTRIBUTE))
                .isNull();
    }
}
