package vn.tsolve.search.embedding.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgVectorTest {
    @Test
    void writesVectorLiteral_AC_005_4() {
        assertThat(PgVector.literal(new float[] {1.25f, -2f, 0f})).isEqualTo("[1.25,-2.0,0.0]");
    }
}
