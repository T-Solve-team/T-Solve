package vn.tsolve.review.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ReviewRegistrationTest {
    @Test
    void normalizesAbsentEvidenceAndCopiesProvidedList_BUG_035() {
        var absent = new ReviewRegistration(null, null, 1, null, null, null, null, null, null, null, null);
        assertThat(absent.evidence()).isEmpty();
        var files = new ArrayList<ReviewRegistration.EvidenceReference>();
        files.add(new ReviewRegistration.EvidenceReference("e1", "file.png", "image/png", 1));
        var provided = new ReviewRegistration(null, null, 1, null, null, null, null, null, files, null, null);
        files.clear();
        assertThat(provided.evidence()).hasSize(1);
        assertThatThrownBy(() -> provided.evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
