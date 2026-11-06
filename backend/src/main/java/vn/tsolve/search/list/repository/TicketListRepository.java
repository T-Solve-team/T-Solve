package vn.tsolve.search.list.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface TicketListRepository
        extends JpaRepository<SearchTicket, Long>, JpaSpecificationExecutor<SearchTicket> {
    @Query("select distinct t.departmentId from SearchTicket t where t.departmentPublicId = :publicId")
    Optional<Long> findDepartmentId(@Param("publicId") UUID publicId);
}
