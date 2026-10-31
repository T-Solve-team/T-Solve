package vn.tsolve.common.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified FIT into a {@link JiraAppAuthentication} with {@code ROLE_JIRA_APP} (AC-001.1). The cloudId is
 * not looked up here: refusing a site of no Workspace belongs to discovery.
 */
public class FitAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt fit) {
        JiraAppPrincipal principal =
                new JiraAppPrincipal(FitValidators.cloudId(fit), fit.getClaimAsString("principal"), apiBaseUrl(fit));
        return new JiraAppAuthentication(principal, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.JIRA_APP)));
    }

    private static String apiBaseUrl(Jwt fit) {
        Map<String, Object> app = fit.getClaimAsMap("app");
        return app != null && app.get("apiBaseUrl") instanceof String url ? url : null;
    }
}
