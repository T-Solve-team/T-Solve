package vn.tsolve.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.KeycloakAuthorizationRequestResolver;
import vn.tsolve.identity.signin.OidcCallbackHandler;
import vn.tsolve.identity.signin.RedisAuthorizationRequestRepository;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The redirect endpoints are public and stateless. The shared web chain protects other requests; the Jira App
 * retains its separate FIT chain.
 */
@Configuration
public class AuthSecurityConfig {

    private static final RequestMatcher SIGN_IN_PATHS = new OrRequestMatcher(
            AntPathRequestMatcher.antMatcher("/api/v1/auth/sign-in"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/authorize"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/callback"),
            AntPathRequestMatcher.antMatcher("/api/v1/auth/complete"));

    private static final Logger log = LoggerFactory.getLogger(AuthSecurityConfig.class);
    private static final String CALLBACK = "/api/v1/auth/callback";

    /**
     * The Keycloak callback on a Workspace site (AC-001.55): stateless, no HTTP session. Other hosts fall through to
     * the chain below; Spring's own {@code /oauth2/authorization/*} and {@code /login/oauth2/code/*} stay outside.
     */
    @Bean
    @Order(0)
    SecurityFilterChain keycloakCallbackChain(
            HttpSecurity http,
            RedisAuthorizationRequestRepository requests,
            OidcCallbackHandler handler,
            WorkspaceLookup workspaces,
            AuthHosts hosts)
            throws Exception {
        http.securityMatcher(request -> AntPathRequestMatcher.antMatcher(CALLBACK)
                                .matches(request)
                        && workspaces.findBySiteHost(hosts.hostOf(request)).isPresent())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(login -> login.loginProcessingUrl(CALLBACK)
                        .authorizedClientRepository(new NoAuthorizedClients())
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(
                                new CheckedRequests(requests, workspaces, hosts)))
                        .successHandler(handler)
                        .failureHandler(handler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable());
        return http.build();
    }

    /**
     * Checks the saved request before Spring reads the callback's {@code error}, and before the code exchange: the
     * state and the Workspace must match (else MSG141), and a Redis failure becomes a sign-in failure (MSG06).
     */
    private record CheckedRequests(
            RedisAuthorizationRequestRepository delegate, WorkspaceLookup workspaces, AuthHosts hosts)
            implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

        @Override
        public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
            return delegate.loadAuthorizationRequest(request);
        }

        @Override
        public void saveAuthorizationRequest(
                OAuth2AuthorizationRequest authorizationRequest,
                HttpServletRequest request,
                HttpServletResponse response) {
            delegate.saveAuthorizationRequest(authorizationRequest, request, response);
        }

        @Override
        public OAuth2AuthorizationRequest removeAuthorizationRequest(
                HttpServletRequest request, HttpServletResponse response) {
            OAuth2AuthorizationRequest saved;
            try {
                saved = delegate.removeAuthorizationRequest(request, response);
            } catch (DataAccessException e) {
                log.error("Redis failed while reading the saved authorization request", e);
                // Not the Internal... subclass: Spring logs that one again at ERROR.
                throw new AuthenticationServiceException("Redis is not available", e);
            }
            if (saved == null) {
                return null;
            }
            String workspaceId = workspaces
                    .findBySiteHost(hosts.hostOf(request))
                    .map(WorkspaceRef::id)
                    .map(Object::toString)
                    .orElse(null);
            boolean matches = saved.getState().equals(request.getParameter("state"))
                    && workspaceId != null
                    && workspaceId.equals(saved.getAttribute(KeycloakAuthorizationRequestResolver.WORKSPACE_ID));
            if (!matches) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_state_parameter"));
            }
            request.setAttribute(OidcCallbackHandler.REQUEST_CHECKED_ATTRIBUTE, Boolean.TRUE);
            return saved;
        }
    }

    /** The login filter keeps no authorized client (access and refresh token): nothing is stored (Q2 A). */
    private static final class NoAuthorizedClients implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                String clientRegistrationId, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient authorizedClient,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(
                String clientRegistrationId,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}
    }

    @Bean
    @Order(1)
    SecurityFilterChain signInRedirectChain(HttpSecurity http) throws Exception {
        http.securityMatcher(SIGN_IN_PATHS)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
