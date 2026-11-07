package vn.tsolve.identity.dev;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.signin.OidcCallbackHandler;

/**
 * Dev-profile answer to a verified Keycloak sign-in. It only works through the callback's forward, which sets the
 * claims attribute. Boot 3.3 filters forwards too; the forward passes because the sign-in is still in the security
 * context of that request. A direct request is not signed in and gets a 401.
 */
@Profile("dev")
@RestController
public class DevClaimsController {

    @GetMapping(OidcCallbackHandler.DEV_CLAIMS_PATH)
    public Map<String, Object> claims(
            @RequestAttribute(OidcCallbackHandler.CLAIMS_ATTRIBUTE) Map<String, Object> claims) {
        return claims;
    }
}
