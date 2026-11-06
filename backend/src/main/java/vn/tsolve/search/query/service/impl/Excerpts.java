package vn.tsolve.search.query.service.impl;

public final class Excerpts {
    private Excerpts() {}

    public static String of(String symptoms, String resolutionSteps) {
        String text = ((symptoms == null ? "" : symptoms) + " " + (resolutionSteps == null ? "" : resolutionSteps))
                .replaceAll("\\s+", " ")
                .trim();
        return text.substring(0, Math.min(200, text.length()));
    }
}
