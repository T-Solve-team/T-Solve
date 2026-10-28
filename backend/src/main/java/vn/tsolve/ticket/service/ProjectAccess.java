package vn.tsolve.ticket.service;

/**
 * What the import needs to know about Projects. The workspace module owns Projects; until it exists a stub answers
 * (design: Stub safety).
 */
public interface ProjectAccess {

    /** True when the user is the Project Manager of this Project and the Project is Active. */
    boolean managesActive(String user, long projectId);

    /** False when the Project was removed or its Department deleted (MSG40). */
    boolean isActive(long projectId);
}
