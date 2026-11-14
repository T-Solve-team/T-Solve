package vn.tsolve.wiki.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.entity.Folder;

@Mapper
public interface WikiFolderMapper {
    @Mapping(target = "folderId", source = "id")
    WikiFolder toResponse(Folder folder);
}
