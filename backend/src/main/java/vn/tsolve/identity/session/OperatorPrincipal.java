package vn.tsolve.identity.session;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/** Who an Operator session is on {@code admin.}: an email, no Account, no Workspace and no Role (AC-001.29). */
public record OperatorPrincipal(String email) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return email;
    }
}
