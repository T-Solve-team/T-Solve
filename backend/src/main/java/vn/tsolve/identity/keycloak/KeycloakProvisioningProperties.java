package vn.tsolve.identity.keycloak;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * What the Keycloak adapter needs (AC-001.61). Loaded only in the {@code ops} profile, so a process that serves a
 * Workspace site never reads the provisioner secret or the Google client values. With one of the three secrets empty
 * the no-op bean is wired instead and one WARN names the missing variable (AC-001.60). With all three set, an unusable
 * {@code TSOLVE_LOGIN_ORIGIN} or {@code TSOLVE_SITE_ORIGIN_TEMPLATE} stops the start-up; the message names the variable
 * and never its value.
 */
@Profile("ops")
@ConfigurationProperties(KeycloakProvisioningProperties.PREFIX)
public record KeycloakProvisioningProperties(
        String baseUrl,
        String realm,
        String adminClientSecret,
        String googleClientId,
        String googleClientSecret,
        String siteOriginTemplate) {

    static final String PREFIX = "tsolve.keycloak-provisioning";

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakProvisioningProperties.class);

    public KeycloakProvisioningProperties {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(adminClientSecret)) {
            missing.add("KEYCLOAK_ADMIN_CLIENT_SECRET");
        }
        if (!StringUtils.hasText(googleClientId)) {
            missing.add("GOOGLE_CLIENT_ID");
        }
        if (!StringUtils.hasText(googleClientSecret)) {
            missing.add("GOOGLE_CLIENT_SECRET");
        }
        if (!missing.isEmpty()) {
            LOG.warn("Keycloak provisioning is off: set {} to turn it on", String.join(", ", missing));
        } else {
            if (!isSecureOrLoopback(baseUrl)) {
                throw new IllegalStateException(
                        "TSOLVE_LOGIN_ORIGIN must be an https origin (http only for localhost or 127.0.0.1)");
            }
            if (siteOriginTemplate == null || !siteOriginTemplate.contains("{slug}")) {
                throw new IllegalStateException("TSOLVE_SITE_ORIGIN_TEMPLATE must be set and contain {slug}");
            }
        }
    }

    @Override
    public String toString() {
        return "KeycloakProvisioningProperties[baseUrl=" + baseUrl + ", realm=" + realm + "]";
    }

    private static boolean isSecureOrLoopback(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme()) && (host.equals("localhost") || host.equals("127.0.0.1"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
