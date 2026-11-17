package vn.tsolve.chatbot.issue.service;

import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotIssueService {
    ChatbotIssueDraftResponse draft(TSolveUser user, String answerId, ChatbotIssueDraftRequest request);
}
