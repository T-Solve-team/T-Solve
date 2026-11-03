package vn.tsolve.common.security;

/** Role names for {@code @PreAuthorize} and the security chains, without the {@code ROLE_} prefix. */
public final class Roles {

    /** A call from our Forge app, carrying a valid FIT from a registered Jira site. */
    public static final String JIRA_APP = "JIRA_APP";

    private Roles() {}
}
