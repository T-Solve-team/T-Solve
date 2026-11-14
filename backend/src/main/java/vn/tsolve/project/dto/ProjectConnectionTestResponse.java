package vn.tsolve.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import vn.tsolve.tracker.dto.TrackerStatus;

public record ProjectConnectionTestResponse(
        String jiraProjectKey,
        List<TrackerStatus> statuses,
        boolean reconnect,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID projectId,
        List<String> doneStatusIds,
        int reviewRange) {}
