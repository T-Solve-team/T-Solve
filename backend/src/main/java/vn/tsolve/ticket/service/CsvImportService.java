package vn.tsolve.ticket.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.entity.ImportType;

public interface CsvImportService {

    /** Checks and parses one CSV for a Project the user manages, and replaces their preview with it (UC-21 3–5). */
    ImportPreviewResponse upload(String user, UUID projectId, ImportType importType, MultipartFile file);

    /** The CSV template of the type: the header row in template order, no data rows (AC-005.1, AC-012.2). */
    String template(ImportType importType);
}
