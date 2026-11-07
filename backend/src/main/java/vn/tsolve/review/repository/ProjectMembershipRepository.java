package vn.tsolve.review.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.tsolve.review.entity.ProjectMembership;
import vn.tsolve.review.entity.ProjectMembershipId;

public interface ProjectMembershipRepository extends Repository<ProjectMembership, ProjectMembershipId> {

    /** One row per ACTIVE Project of the Workspace, with whether the Account manages it. */
    interface ProjectRow {
        UUID getPublicId();

        UUID getLegacyId();

        String getProjectKey();

        UUID getDepartmentId();

        boolean getManaged();
    }

    @Query(
            value =
                    """
                    SELECT p.public_id AS "publicId",
                           lpad(to_hex(p.project_id), 32, '0')::uuid AS "legacyId",
                           p.jira_project_key AS "projectKey",
                           d.public_id AS "departmentId",
                           EXISTS (SELECT 1 FROM project_membership m
                                   WHERE m.project_id = p.public_id AND m.user_id = :accountId
                                     AND m.role_in_project = 'PROJECT_MANAGER') AS managed
                    FROM project p JOIN department d ON d.department_id = p.department_id
                    WHERE p.status = 'ACTIVE'
                    ORDER BY p.jira_project_key
                    """,
            nativeQuery = true)
    List<ProjectRow> activeProjects(@Param("accountId") UUID accountId);

    @Query(value = "SELECT count(*) > 0 FROM app_user WHERE id = :accountId AND status = 'ACTIVE'", nativeQuery = true)
    boolean isActiveAccount(@Param("accountId") UUID accountId);
}
