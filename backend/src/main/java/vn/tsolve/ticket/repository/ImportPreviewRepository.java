package vn.tsolve.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.ticket.entity.ImportPreview;

public interface ImportPreviewRepository extends JpaRepository<ImportPreview, Long> {

    Optional<ImportPreview> findByIdAndOwner(Long id, String owner);

    // rows go with it through ON DELETE CASCADE
    @Modifying
    @Query("delete from ImportPreview p where p.owner = :owner")
    int deleteByOwner(@Param("owner") String owner);

    @Query("select p.id from ImportPreview p where p.createdAt < :before")
    List<Long> findIdsCreatedBefore(@Param("before") Instant before);
}
