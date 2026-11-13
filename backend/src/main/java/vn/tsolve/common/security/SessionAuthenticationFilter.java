package vn.tsolve.common.security;

import jakarta.servlet.Filter;

/**
 * Marker for the filter that turns a session cookie into the signed-in user. A module (identity) implements it as a
 * bean; {@link SecurityConfig} adds it to the web chain without knowing the module.
 */
public interface SessionAuthenticationFilter extends Filter {}
