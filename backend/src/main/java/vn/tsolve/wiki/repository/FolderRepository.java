package vn.tsolve.wiki.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.wiki.entity.Folder;

public interface FolderRepository extends JpaRepository<Folder, UUID> {}
