package vn.tsolve.ticket.service;

import java.time.LocalDate;
import java.util.UUID;
import vn.tsolve.ticket.dto.ImportPreviewResponse;

public interface JiraImportService {

    /**
     * Pulls the resolved Jira issues of a Project the user manages that have no Ticket yet (at most 100, oldest
     * first) and replaces the user's open preview with them (UC-20, AC-001.1…AC-001.10).
     */
    ImportPreviewResponse pull(String user, UUID projectId, LocalDate from, LocalDate to);
}
