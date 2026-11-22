package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.jiraapp.service.impl.DraftReminderDispatchHandler;

/** The daily run of the Draft reminder (jira-app AC-011.1): one Workspace run per active Workspace. */
public record DraftReminderDispatchRequest() implements JobRequest {
    @Override
    @JsonIgnore
    public Class<DraftReminderDispatchHandler> getJobRequestHandler() {
        return DraftReminderDispatchHandler.class;
    }
}
