package vn.tsolve.common.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified FIT into a {@link JiraAppAuthentication}. Only a FIT from a registered Jira site gets
 * {@code ROLE_JIRA_APP}; any other is authenticated without it, so the chain answers 403 (AC-001.6).
 */
public class FitAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JiraSiteLookup siteLookup;

    public FitAuthenticationConverter(JiraSiteLookup siteLookup) {
        this.siteLookup = siteLookup;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt fit) {
        String cloudId = FitValidators.cloudId(fit);
        long workspaceId = siteLookup.findWorkspaceId(cloudId).orElse(WorkspaceContext.NONE);
        JiraAppPrincipal principal =
                new JiraAppPrincipal(cloudId, workspaceId, fit.getClaimAsString("principal"), apiBaseUrl(fit));
        return new JiraAppAuthentication(
                principal,
                principal.isRegisteredSite()
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP))
                        : List.of());
    }

    private static String apiBaseUrl(Jwt fit) {
        Map<String, Object> app = fit.getClaimAsMap("app");
        return app != null && app.get("apiBaseUrl") instanceof String url ? url : null;
    }
}
