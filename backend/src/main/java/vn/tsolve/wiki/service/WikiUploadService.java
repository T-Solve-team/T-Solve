package vn.tsolve.wiki.service;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.wiki.dto.WikiDocumentFile;
import vn.tsolve.wiki.dto.WikiDocumentRef;
import vn.tsolve.wiki.dto.WikiUploadPreview;

public interface WikiUploadService {
    List<WikiUploadPreview> extract(UUID userId, UUID spaceId, List<MultipartFile> files);

    List<WikiDocumentRef> publish(UUID userId, UUID spaceId, List<MultipartFile> files, String metadata);

    WikiDocumentFile download(UUID userId, UUID documentId);
}
