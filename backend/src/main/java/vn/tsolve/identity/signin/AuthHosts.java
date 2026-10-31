package vn.tsolve.identity.signin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Tells which configured origin a request came to: the central sign-in ({@code login.}), the Platform Console
 * ({@code admin.}), or any other host, which is a Workspace site. Only host names are compared; the port is not part
 * of a site's name and a proxy may change it.
 */
@Component
public class AuthHosts {

    private final String loginHost;
    private final String adminHost;

    public AuthHosts(SignInProperties properties) {
        this.loginHost = lower(properties.loginOrigin().getHost());
        this.adminHost = lower(properties.adminOrigin().getHost());
    }

    /** The host the request was sent to, lower case and without a port. */
    public String hostOf(HttpServletRequest request) {
        String host = lower(request.getServerName());
        int colon = host.lastIndexOf(':');
        boolean hasPort = colon > 0 && host.indexOf(']') < colon && host.indexOf(':') == colon;
        return hasPort ? host.substring(0, colon) : host;
    }

    public boolean isLoginHost(HttpServletRequest request) {
        return hostOf(request).equals(loginHost);
    }

    public boolean isAdminHost(HttpServletRequest request) {
        return hostOf(request).equals(adminHost);
    }

    private static String lower(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }
}
