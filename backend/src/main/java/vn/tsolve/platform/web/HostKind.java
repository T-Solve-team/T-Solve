package vn.tsolve.platform.web;

/** What the raw {@code Host} header of a web request names. */
public sealed interface HostKind {

    /** A Workspace site; the label is lower case and not yet looked up. */
    record Site(String label) implements HostKind {}

    record Login() implements HostKind {}

    record Api() implements HostKind {}

    record Admin() implements HostKind {}

    /** No Workspace site: bare, {@code www.}, {@code staging.}, deep, foreign or missing host, or an invalid label. */
    record NotASite() implements HostKind {}
}
