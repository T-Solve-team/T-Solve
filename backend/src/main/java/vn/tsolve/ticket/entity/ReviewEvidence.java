package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import vn.tsolve.ticket.dto.ReviewRegistration.EvidenceReference;

@Embeddable
public class ReviewEvidence {
    @Column(name = "evidence_id", nullable = false)
    private String evidenceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected ReviewEvidence() {}

    public ReviewEvidence(EvidenceReference reference) {
        if (reference.evidenceId() == null
                || reference.evidenceId().isBlank()
                || reference.name() == null
                || reference.name().isBlank()
                || reference.mediaType() == null
                || reference.mediaType().isBlank()
                || reference.sizeBytes() < 0
                || reference.sizeBytes() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid saved Evidence metadata");
        }
        evidenceId = reference.evidenceId();
        name = reference.name();
        mediaType = reference.mediaType();
        sizeBytes = reference.sizeBytes();
    }

    public EvidenceReference toReference() {
        return new EvidenceReference(evidenceId, name, mediaType, sizeBytes);
    }
}
