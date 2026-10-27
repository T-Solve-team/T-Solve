package vn.tsolve.common.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** A verified FIT. The token itself is not kept, so it cannot reach a log or a response (AC-001.7). */
public class JiraAppAuthentication extends AbstractAuthenticationToken {

    private final JiraAppPrincipal principal;

    public JiraAppAuthentication(JiraAppPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public JiraAppPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
