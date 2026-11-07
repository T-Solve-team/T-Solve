package vn.tsolve.search.query.service.impl;

public final class Excerpts {
    private Excerpts() {}

    public static String of(String symptoms, String resolutionSteps) {
        String text = ((symptoms == null ? "" : symptoms) + " " + (resolutionSteps == null ? "" : resolutionSteps))
                .replaceAll("\\s+", " ")
                .trim();
        return text.codePointCount(0, text.length()) > 200 ? text.substring(0, text.offsetByCodePoints(0, 200)) : text;
    }
}
