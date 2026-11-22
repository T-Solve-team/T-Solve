package vn.tsolve.workspace.site;

/**
 * Replacement port for platform {@code WorkspaceCatalog.linkJiraSite} (AC-004.19..24): read tenant information
 * for the normalised HTTPS site, return its cloudId and bind it to the current Workspace. The catalog refuses
 * unreadable sites, changes to an existing link, and links owned by another Workspace, including concurrent links.
 * It stores nothing on refusal and never identifies another Workspace. To be replaced by that platform service;
 * the interim implementation only reads the cloudId and cannot enforce catalog ownership.
 */
public interface JiraSiteLinker {

    /**
     * @param normalisedSiteUrl a site already normalised by {@link JiraSiteRule}
     * @return the site's cloudId
     * @throws JiraSiteLinkRefusedException when the link is refused or tenant information cannot be read
     */
    String linkJiraSite(String normalisedSiteUrl) throws JiraSiteLinkRefusedException;

    /**
     * Reads the site's cloud id without linking it (AC-011.8, KAN-344): the service account check needs it before the
     * link. Only a canonical lower-case UUID is returned, so it can never change the gateway path.
     *
     * @param normalisedSiteUrl a site already normalised by {@link JiraSiteRule}
     * @return the site's cloudId
     * @throws JiraSiteLinkRefusedException ({@code UNREADABLE}) when it cannot be read or is not a canonical UUID
     */
    String readCloudId(String normalisedSiteUrl) throws JiraSiteLinkRefusedException;
}
