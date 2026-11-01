package vn.tsolve.project.entity;

/** A Project is never deleted, only REMOVED (DB design 31/10). */
public enum ProjectStatus {
    ACTIVE,
    REMOVED
}
