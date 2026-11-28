package vn.tsolve.platform.web;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads a raw {@code Host} header against the base domain of {@code TSOLVE_SITE_ORIGIN_TEMPLATE} (AC-002.2). */
public final class HostClassifier {

    private static final String SLUG_TOKEN = "{slug}";
    private static final String PLACEHOLDER = "tsolve-slug-placeholder";
    private static final Pattern PORT = Pattern.compile(":[0-9]*$");
    // One RFC 1123 label: no dot, so a deeper host is no site.
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private final String baseDomain;

    private HostClassifier(String baseDomain) {
        this.baseDomain = baseDomain;
    }

    /** @throws IllegalStateException when {@code {slug}} is not the first label of the template's host */
    public static HostClassifier fromTemplate(String siteOriginTemplate) {
        if (siteOriginTemplate != null
                && siteOriginTemplate.indexOf(SLUG_TOKEN) == siteOriginTemplate.lastIndexOf(SLUG_TOKEN)) {
            try {
                String host = URI.create(siteOriginTemplate.replace(SLUG_TOKEN, PLACEHOLDER))
                        .getHost();
                String prefix = PLACEHOLDER + ".";
                if (host != null && host.startsWith(prefix) && host.length() > prefix.length()) {
                    return new HostClassifier(host.substring(prefix.length()).toLowerCase(Locale.ROOT));
                }
            } catch (IllegalArgumentException notAUri) {
                // Not chained: its message holds the value.
            }
        }
        throw new IllegalStateException(
                "TSOLVE_SITE_ORIGIN_TEMPLATE must be an origin URL whose first host label is " + SLUG_TOKEN);
    }

    public String baseDomain() {
        return baseDomain;
    }

    /** Case and port are ignored; a trailing dot is no site. */
    public HostKind classify(String rawHost) {
        if (rawHost == null) {
            return new HostKind.NotASite();
        }
        String host = PORT.matcher(rawHost.toLowerCase(Locale.ROOT)).replaceFirst("");
        String suffix = "." + baseDomain;
        if (!host.endsWith(suffix)) {
            return new HostKind.NotASite();
        }
        String label = host.substring(0, host.length() - suffix.length());
        if (!LABEL.matcher(label).matches()) {
            return new HostKind.NotASite();
        }
        return switch (label) {
            case "login" -> new HostKind.Login();
            case "api" -> new HostKind.Api();
            case "admin" -> new HostKind.Admin();
            case "www", "staging" -> new HostKind.NotASite();
            default -> new HostKind.Site(label);
        };
    }
}
