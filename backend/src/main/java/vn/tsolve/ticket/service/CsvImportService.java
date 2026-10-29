package vn.tsolve.ticket.service;

import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.ticket.dto.ImportPreviewResponse;

public interface CsvImportService {

    /** Checks and parses one CSV for a Project the user manages, and replaces their preview with it (UC-21 3–5). */
    ImportPreviewResponse upload(String user, long projectId, MultipartFile file);

    /** The CSV template: the header row in template order, no data rows (AC-005.1). */
    String template();
}
