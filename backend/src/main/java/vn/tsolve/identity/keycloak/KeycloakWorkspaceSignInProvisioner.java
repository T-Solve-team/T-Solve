package vn.tsolve.identity.keycloak;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.keycloak.KeycloakAdminClient.ClientUris;
import vn.tsolve.identity.keycloak.KeycloakAdminClient.OrganizationRef;
import vn.tsolve.identity.signin.port.ProvisioningWiring;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;

/**
 * The Keycloak adapter of the port (AC-001.57 to .59, .62). Every operation runs under the advisory lock. {@code
 * provision} creates Organization, identity provider and the two {@code tsolve-web} URIs, re-reads the client after
 * the URI write, and on any failure deletes what it created in reverse order. Objects of a Workspace are found by the
 * {@code tsolve.workspace-id} tag, never by a name the caller could pick.
 */
@Component
@Conditional(ProvisioningWiring.Configured.class)
public class KeycloakWorkspaceSignInProvisioner implements WorkspaceSignInProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakWorkspaceSignInProvisioner.class);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?");

    private final KeycloakAdminClient client;
    private final ProvisioningLock lock;
    private final KeycloakProvisioningProperties properties;

    public KeycloakWorkspaceSignInProvisioner(
            KeycloakAdminClient client, ProvisioningLock lock, KeycloakProvisioningProperties properties) {
        this.client = client;
        this.lock = lock;
        this.properties = properties;
    }

    @Override
    public Optional<String> provision(UUID workspaceId, String siteName, String googleDomain) {
        requireValidSlug(siteName);
        if (googleDomain == null || googleDomain.isBlank()) {
            throw new IllegalArgumentException("The Google domain is required");
        }
        String callback = callbackUri(siteName);
        String logout = logoutUri(siteName);
        return Optional.of(
                lock.runLocked(() -> provisionLocked(workspaceId, siteName, googleDomain, callback, logout)));
    }

    @Override
    public ProvisionedObjects check(UUID workspaceId) {
        Optional<OrganizationRef> organization = client.findOrganization(workspaceId);
        Optional<String> idp = client.findIdentityProviderAlias(workspaceId, organization.map(OrganizationRef::alias));
        Optional<String> slug = slugOf(organization, idp);
        boolean callback = false;
        boolean logout = false;
        if (slug.isPresent()) {
            ClientUris uris = client.readWebClientUris();
            callback = uris.redirectUris().contains(callbackUri(slug.get()));
            logout = uris.postLogoutRedirectUris().contains(logoutUri(slug.get()));
        }
        return new ProvisionedObjects(organization.isPresent(), idp.isPresent(), callback, logout);
    }

    @Override
    public void remove(UUID workspaceId) {
        lock.runLocked(() -> {
            Optional<OrganizationRef> organization = client.findOrganization(workspaceId);
            Optional<String> idp =
                    client.findIdentityProviderAlias(workspaceId, organization.map(OrganizationRef::alias));
            idp.ifPresent(client::deleteIdentityProvider);
            Optional<String> slug = slugOf(organization, idp);
            if (slug.isPresent() && !removeOwnUris(workspaceId, slug.get()).isEmpty()) {
                throw new IllegalStateException("tsolve-web lost URIs while a Workspace was removed");
            }
            organization.ifPresent(o -> {
                client.deleteOrganizationUsers(o.id());
                client.deleteOrganization(o.id());
            });
            return null;
        });
    }

    private String provisionLocked(UUID workspaceId, String slug, String domain, String callback, String logout) {
        boolean createdOrganization = false;
        boolean triedIdentityProvider = false;
        boolean triedUris = false;
        String organizationId = null;
        try {
            Optional<OrganizationRef> existing = client.findOrganization(workspaceId);
            if (existing.isPresent()) {
                requireSame(existing.get(), slug, domain);
                organizationId = existing.get().id();
            } else {
                // no Organization, so a tagged identity provider is a leftover that nothing links to a new one
                if (client.findIdentityProviderAlias(workspaceId, Optional.empty()).isPresent()) {
                    throw new IllegalStateException(
                            "The Workspace has an identity provider but no Organization; remove it first");
                }
                createdOrganization = true;
                organizationId = client.createOrganization(workspaceId, slug, domain);
            }

            if (client.findIdentityProviderAlias(workspaceId, Optional.of(slug)).isEmpty()) {
                triedIdentityProvider = true;
                client.createGoogleIdentityProvider(slug, workspaceId, domain, organizationId);
            }

            ClientUris before = client.readWebClientUris();
            List<String> redirect = new ArrayList<>(before.redirectUris());
            List<String> postLogout = new ArrayList<>(before.postLogoutRedirectUris());
            boolean missingCallback = !redirect.contains(callback);
            boolean missingLogout = !postLogout.contains(logout);
            if (missingCallback || missingLogout) {
                if (missingCallback) {
                    redirect.add(callback);
                }
                if (missingLogout) {
                    postLogout.add(logout);
                }
                triedUris = true;
                client.writeWebClientUris(new ClientUris(redirect, postLogout));
                if (!lostAfterWrite(workspaceId, redirect, postLogout).isEmpty()) {
                    throw new IllegalStateException("tsolve-web lost URIs after the write");
                }
            }
            return organizationId;
        } catch (RuntimeException failure) {
            LOG.warn(
                    "Provisioning the Workspace {} failed ({}); deleting what it created",
                    workspaceId,
                    failure.getClass().getSimpleName());
            String createdOrganizationId = createdOrganization ? organizationId : null;
            if (triedUris) {
                undoStep(failure, workspaceId, "remove the tsolve-web URIs", () -> removeOwnUris(workspaceId, slug));
            }
            if (triedIdentityProvider) {
                undoStep(failure, workspaceId, "delete the identity provider", () -> deleteIdp(workspaceId, slug));
            }
            if (createdOrganizationId != null) {
                undoStep(
                        failure,
                        workspaceId,
                        "delete the Organization",
                        () -> client.deleteOrganization(createdOrganizationId));
            }
            throw failure;
        }
    }

    /**
     * Re-reads {@code tsolve-web} after a write: every URI that was written must still be there. A lost URI is logged
     * once at ERROR, is not put back, and is returned.
     */
    private Set<String> lostAfterWrite(UUID workspaceId, List<String> writtenRedirect, List<String> writtenLogout) {
        ClientUris after = client.readWebClientUris();
        Set<String> lost = new LinkedHashSet<>();
        writtenRedirect.stream().filter(u -> !after.redirectUris().contains(u)).forEach(lost::add);
        writtenLogout.stream().filter(u -> !after.postLogoutRedirectUris().contains(u)).forEach(lost::add);
        if (!lost.isEmpty()) {
            LOG.error(
                    "tsolve-web lost URIs after a write for the Workspace {} and they are not put back: {}",
                    workspaceId,
                    lost);
        }
        return lost;
    }

    private void deleteIdp(UUID workspaceId, String slug) {
        client.findIdentityProviderAlias(workspaceId, Optional.of(slug)).ifPresent(client::deleteIdentityProvider);
    }

    /** One undo step: a failure is logged at ERROR, kept on the original failure, and the next step still runs. */
    private void undoStep(RuntimeException failure, UUID workspaceId, String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException undoFailure) {
            LOG.error(
                    "Undo step '{}' failed for the Workspace {} ({})",
                    step,
                    workspaceId,
                    undoFailure.getClass().getSimpleName());
            failure.addSuppressed(undoFailure);
        }
    }

    /** Removes this Workspace's two URIs from {@code tsolve-web}; returns the URIs the re-read found lost. */
    private Set<String> removeOwnUris(UUID workspaceId, String slug) {
        String callback = callbackUri(slug);
        String logout = logoutUri(slug);
        ClientUris current = client.readWebClientUris();
        if (!current.redirectUris().contains(callback) && !current.postLogoutRedirectUris().contains(logout)) {
            return Set.of();
        }
        List<String> redirect = new ArrayList<>(current.redirectUris());
        List<String> postLogout = new ArrayList<>(current.postLogoutRedirectUris());
        redirect.remove(callback);
        postLogout.remove(logout);
        client.writeWebClientUris(new ClientUris(redirect, postLogout));
        return lostAfterWrite(workspaceId, redirect, postLogout);
    }

    private static void requireSame(OrganizationRef existing, String slug, String domain) {
        if (!slug.equals(existing.alias())
                || existing.domains().size() != 1
                || !domain.equals(existing.domains().get(0))) {
            throw new IllegalStateException("The Workspace is already provisioned with another site name or domain");
        }
    }

    private static Optional<String> slugOf(Optional<OrganizationRef> organization, Optional<String> idpAlias) {
        if (organization.isPresent()) {
            return Optional.of(organization.get().alias());
        }
        return idpAlias.map(alias -> alias.substring(KeycloakAdminClient.IDP_ALIAS_PREFIX.length()));
    }

    private String callbackUri(String slug) {
        return origin(slug) + "/api/v1/auth/callback";
    }

    private String logoutUri(String slug) {
        return origin(slug) + "/login";
    }

    private String origin(String slug) {
        String template = properties.siteOriginTemplate();
        if (template == null || !template.contains("{slug}")) {
            throw new IllegalStateException("TSOLVE_SITE_ORIGIN_TEMPLATE must be set and contain {slug}");
        }
        return template.replace("{slug}", slug);
    }

    private static void requireValidSlug(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("The site name must be lower-case letters, digits and hyphens");
        }
    }
}
