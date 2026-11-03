package vn.tsolve.identity.signin;

/** The verified identity claims from a Google ID token. */
public record GoogleIdentity(String subject, String email, String hostedDomain, String name) {}
