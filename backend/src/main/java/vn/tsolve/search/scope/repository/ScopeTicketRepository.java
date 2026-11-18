package vn.tsolve.search.scope.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface ScopeTicketRepository extends Repository<SearchTicket, UUID> {
    @Query("select distinct t.departmentId from SearchTicket t where t.departmentStatus = 'ACTIVE'")
    Set<Long> activeDepartments();

    @Query("select distinct t.projectId from SearchTicket t")
    Set<Long> projects();

    /** An ACTIVE Department: its internal id for the scope, its public id for identity's access check. */
    interface ActiveDepartment {
        Long getDepartmentId();

        UUID getPublicId();
    }

    @Query(value = """
                    SELECT department_id AS "departmentId", public_id AS "publicId"
                    FROM department WHERE status = 'ACTIVE'
                    """, nativeQuery = true)
    List<ActiveDepartment> activeDepartmentRefs();

    @Query(value = "SELECT project_id FROM project WHERE public_id IN (:publicIds)", nativeQuery = true)
    Set<Long> projectIds(@Param("publicIds") Collection<UUID> publicIds);
}
