package vn.tsolve.workspace.site;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class JiraSiteRule {

    private static final Pattern JIRA_HOST = Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.atlassian\\.net");

    private JiraSiteRule() {}

    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            URI site = new URI(raw.trim().toLowerCase(Locale.ROOT));
            String host = site.getHost();
            if (!"https".equals(site.getScheme())
                    || host == null
                    || !JIRA_HOST.matcher(host).matches()
                    || site.getRawUserInfo() != null
                    || site.getPort() != -1
                    // URI reports no port for a trailing colon too; accept only a bare host authority.
                    || !host.equals(site.getRawAuthority())
                    || site.getRawQuery() != null
                    || site.getRawFragment() != null
                    || !(site.getRawPath().isEmpty() || "/".equals(site.getRawPath()))) {
                return Optional.empty();
            }
            return Optional.of("https://" + host);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
