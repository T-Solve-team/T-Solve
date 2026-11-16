package vn.tsolve.wiki.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.entity.WikiDocument;

@Mapper
public interface WikiDocumentMapper {
    @Mapping(target = "documentId", source = "id")
    WikiDocumentRef toRef(WikiDocument document);
}
