package vn.tsolve.search.scope.repository;

import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface ScopeTicketRepository extends Repository<SearchTicket, Long> {
    @Query("select distinct t.departmentId from SearchTicket t where t.departmentStatus = 'ACTIVE'")
    Set<Long> activeDepartments();

    @Query("select distinct t.projectId from SearchTicket t")
    Set<UUID> projects();
}
