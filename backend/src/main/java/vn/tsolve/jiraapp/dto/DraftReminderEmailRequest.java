package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.jiraapp.service.impl.DraftReminderEmailHandler;

/**
 * One writer's Draft reminder email for one day (AC-011.2). It carries ids only: the address and the Drafts are read
 * when the job runs, so nothing personal sits in the job store.
 */
public record DraftReminderEmailRequest(UUID workspaceId, UUID accountId, LocalDate day) implements JobRequest {
    @Override
    @JsonIgnore
    public Class<DraftReminderEmailHandler> getJobRequestHandler() {
        return DraftReminderEmailHandler.class;
    }
}
