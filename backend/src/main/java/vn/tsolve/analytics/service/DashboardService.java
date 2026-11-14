package vn.tsolve.analytics.service;

import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.common.security.TSolveUser;

public interface DashboardService {
    KnowledgeDashboardResponse dashboard(
            TSolveUser user, String from, String to, String departmentId, String projectId);
}
