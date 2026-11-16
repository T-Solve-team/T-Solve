package vn.tsolve.wiki.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.wiki.entity.Folder;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    long countByParentId(UUID parentId);

    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT f.* FROM folder f WHERE f.folder_id = :folderId
                UNION ALL
                SELECT child.* FROM folder child JOIN subtree parent ON child.parent_id = parent.folder_id
            ) SELECT * FROM subtree
            """, nativeQuery = true)
    List<Folder> findSubtree(@Param("folderId") UUID folderId);
}
