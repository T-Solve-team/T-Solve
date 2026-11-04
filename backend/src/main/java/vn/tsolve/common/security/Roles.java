package vn.tsolve.common.security;

/** Role names for {@code @PreAuthorize} and the security chains, without the {@code ROLE_} prefix. */
public final class Roles {
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String TICKET_RESOLVER = "TICKET_RESOLVER";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String KNOWLEDGE_MANAGER = "KNOWLEDGE_MANAGER";
    public static final String ADMIN = "ADMIN";

    /** A call from our Forge app, carrying a valid FIT from a registered Jira site. */
    public static final String JIRA_APP = "JIRA_APP";

    private Roles() {}
}
