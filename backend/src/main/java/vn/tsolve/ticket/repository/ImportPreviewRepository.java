package vn.tsolve.ticket.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ImportPreview;

public interface ImportPreviewRepository extends JpaRepository<ImportPreview, Long> {

    Optional<ImportPreview> findByIdAndOwner(Long id, String owner);

    /** Locks the preview row until the transaction ends, so a second Confirm of it waits (AC-004.12). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ImportPreview p where p.id = :id and p.owner = :owner")
    Optional<ImportPreview> lockByIdAndOwner(@Param("id") Long id, @Param("owner") String owner);

    /** The caller's open preview, before a new upload or pull; Drafts stay (AC-004.6). Rows go through ON DELETE CASCADE. */
    @Modifying
    @Query("delete from ImportPreview p where p.owner = :owner and p.draftSavedAt is null")
    int deleteByOwner(@Param("owner") String owner);

    /** Previews for the daily cleanup; Drafts stay (AC-004.6). */
    @Query("select p.id from ImportPreview p where p.createdAt < :before and p.draftSavedAt is null")
    List<Long> findIdsCreatedBefore(@Param("before") Instant before);

    List<ImportPreview> findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc(String owner);
}
