package vn.tsolve.chatbot.issue.dto;

import java.util.List;

public record ChatbotIssueDraftResponse(
        String summary, String description, Long departmentId, List<IssueDepartment> departments) {}
