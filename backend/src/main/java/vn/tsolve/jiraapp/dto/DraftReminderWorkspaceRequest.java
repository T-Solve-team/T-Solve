package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.jiraapp.service.impl.DraftReminderWorkspaceHandler;

/** The Draft reminder of one Workspace for one day (Asia/Ho_Chi_Minh): one email job per writer (AC-011.2). */
public record DraftReminderWorkspaceRequest(UUID workspaceId, LocalDate day) implements JobRequest {
    @Override
    @JsonIgnore
    public Class<DraftReminderWorkspaceHandler> getJobRequestHandler() {
        return DraftReminderWorkspaceHandler.class;
    }
}
