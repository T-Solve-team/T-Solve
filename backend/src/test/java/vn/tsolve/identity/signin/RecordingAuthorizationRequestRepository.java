package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Test double: records every saved authorization request and never touches Redis or a cookie. */
public final class RecordingAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final List<OAuth2AuthorizationRequest> saved = new CopyOnWriteArrayList<>();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return saved.isEmpty() ? null : saved.get(saved.size() - 1);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest != null) {
            saved.add(authorizationRequest);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        return null;
    }

    public List<OAuth2AuthorizationRequest> saved() {
        return List.copyOf(saved);
    }

    public OAuth2AuthorizationRequest onlySaved() {
        assertThat(saved).as("saved authorization requests").hasSize(1);
        return saved.get(0);
    }

    public void reset() {
        saved.clear();
    }
}
