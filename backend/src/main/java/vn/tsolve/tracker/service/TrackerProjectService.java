package vn.tsolve.tracker.service;

import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.dto.TrackerProjectLookup;

/** Read-only Jira lookups (KAN-127, KAN-121); client failures are returned as outcomes, never thrown. */
public interface TrackerProjectService {

    TrackerProjectLookup findProject(String projectKey);

    /** Whether the Chatbot can create Task issues in the project as the service account (workspace AC-012.7). */
    ChatbotProjectCheck checkChatbotProject(String projectKey);
}
