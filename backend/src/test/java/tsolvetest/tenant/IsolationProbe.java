package tsolvetest.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Test-only entity on {@code isolation_probes}. It lives outside {@code vn.tsolve} on purpose: the application's entity
 * scan would otherwise pick it up in every {@code @SpringBootTest}, where the table does not exist.
 */
@Entity
@Table(name = "isolation_probes")
public class IsolationProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "isolation_probes_id")
    private UUID id;

    @Column(name = "note", nullable = false)
    private String note;

    protected IsolationProbe() {}

    public IsolationProbe(String note) {
        this.note = note;
    }
}
