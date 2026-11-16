package vn.tsolve.wiki.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.wiki.entity.SpacePermission;

public interface SpacePermissionRepository extends JpaRepository<SpacePermission, UUID> {}
