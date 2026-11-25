package vn.tsolve.chatbot.issue.service;

import vn.tsolve.chatbot.issue.dto.ChatbotIssueCreateResult;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueRequest;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotIssueService {
    /** AC-010.3: the AI reads the turns before the answer in its saved conversation. */
    ChatbotIssueDraftResponse draft(TSolveUser user, String answerId);

    ChatbotIssueCreateResult create(TSolveUser user, String answerId, ChatbotIssueRequest request);
}
