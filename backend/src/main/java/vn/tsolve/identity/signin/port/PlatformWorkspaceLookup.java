package vn.tsolve.identity.signin.port;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** Resolves Workspaces from {@link WorkspaceCatalog} outside the {@code dev} profile. */
@Profile("!dev")
@Component
public final class PlatformWorkspaceLookup implements WorkspaceLookup, SmartInitializingSingleton {

    private static final String SLUG_TOKEN = "{slug}";
    private static final String SLUG_PLACEHOLDER = "tsolve-slug-placeholder";

    private final ObjectProvider<WorkspaceCatalog> catalog;
    private final HostTemplate hostTemplate;

    public PlatformWorkspaceLookup(
            ObjectProvider<WorkspaceCatalog> catalog,
            @Value("${TSOLVE_SITE_ORIGIN_TEMPLATE:}") String siteOriginTemplate) {
        this.catalog = catalog;
        this.hostTemplate = HostTemplate.parse(siteOriginTemplate);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (catalog.getIfAvailable() != null && hostTemplate == null) {
            throw new IllegalStateException(
                    "TSOLVE_SITE_ORIGIN_TEMPLATE must be set, contain no whitespace, and include " + SLUG_TOKEN);
        }
    }

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        String normalized = withoutPort(host);
        if (normalized.isEmpty() || hostTemplate == null) {
            return Optional.empty();
        }
        String slug = hostTemplate.extractSlug(normalized);
        if (slug == null) {
            return Optional.empty();
        }
        WorkspaceCatalog activeCatalog = catalog.getIfAvailable();
        if (activeCatalog == null) {
            return Optional.empty();
        }
        return activeCatalog.findBySiteName(slug).map(this::toRef);
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        if (id == null || hostTemplate == null) {
            return Optional.empty();
        }
        WorkspaceCatalog activeCatalog = catalog.getIfAvailable();
        if (activeCatalog == null) {
            return Optional.empty();
        }
        return activeCatalog.findById(id).map(this::toRef);
    }

    private WorkspaceRef toRef(WorkspaceSummary summary) {
        String siteHost = hostTemplate.hostFor(summary.siteName().toLowerCase(Locale.ROOT));
        WorkspaceRef.Status status =
                switch (summary.status()) {
                    case ACTIVE -> WorkspaceRef.Status.ACTIVE;
                    case SUSPENDED -> WorkspaceRef.Status.SUSPENDED;
                };
        return new WorkspaceRef(
                summary.id(),
                siteHost,
                summary.googleDomain(),
                status,
                summary.siteName(),
                summary.keycloakOrganizationId().orElse(null));
    }

    private static String withoutPort(String host) {
        if (host == null) {
            return "";
        }
        String lower = host.trim().toLowerCase(Locale.ROOT);
        int colon = lower.lastIndexOf(':');
        return colon > 0 && lower.indexOf(':') == colon ? lower.substring(0, colon) : lower;
    }

    private record HostTemplate(String prefix, String suffix) {

        static HostTemplate parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            if (raw.chars().anyMatch(Character::isWhitespace)) {
                return null;
            }
            int first = raw.indexOf(SLUG_TOKEN);
            if (first < 0 || raw.indexOf(SLUG_TOKEN, first + SLUG_TOKEN.length()) >= 0) {
                return null;
            }
            String substituted = raw.replace(SLUG_TOKEN, SLUG_PLACEHOLDER);
            URI uri;
            try {
                uri = URI.create(substituted);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            int placeholderIndex = lowerHost.indexOf(SLUG_PLACEHOLDER);
            if (placeholderIndex < 0) {
                return null;
            }
            return new HostTemplate(
                    lowerHost.substring(0, placeholderIndex),
                    lowerHost.substring(placeholderIndex + SLUG_PLACEHOLDER.length()));
        }

        String extractSlug(String normalizedHost) {
            if (!normalizedHost.startsWith(prefix) || !normalizedHost.endsWith(suffix)) {
                return null;
            }
            int end = normalizedHost.length() - suffix.length();
            if (end <= prefix.length()) {
                return null;
            }
            String slug = normalizedHost.substring(prefix.length(), end);
            if (slug.isEmpty() || slug.indexOf('.') >= 0) {
                return null;
            }
            return slug;
        }

        String hostFor(String slug) {
            return prefix + slug + suffix;
        }
    }
}
