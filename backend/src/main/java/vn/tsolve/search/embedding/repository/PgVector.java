package vn.tsolve.search.embedding.repository;

import java.util.StringJoiner;

public final class PgVector {
    private PgVector() {}

    public static String literal(float[] vector) {
        StringJoiner literal = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Vector components must be finite");
            }
            literal.add(Float.toString(value));
        }
        return literal.toString();
    }
}
