package vn.tsolve.jiraapp.service;

import java.util.UUID;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;

/** The questions of the "Ask T-Solve" Jira project page (STORY-010). No T-Solve sign-in is needed. */
public interface JiraAppQuestionService {

    /** Whether the page can answer in the FIT's site and project; calls no AI (AC-010.2, AC-010.14). */
    JiraAskerEligibility eligibility(JiraAppPrincipal caller);

    /**
     * The Knowledge Chatbot's answer for the Project of the FIT's Jira project (AC-010.4). Throws
     * {@code JiraSiteNotConnectedException} (403 MSG124) or {@code JiraIssueAccessDeniedException} (403 MSG08) before
     * any AI call (AC-010.11) and {@code AiUnavailableException} when the AI fails or misses the deadline (AC-010.8).
     */
    ChatbotAnswerResponse ask(JiraAppPrincipal caller, JiraAppQuestionRequest request);

    /** Rates an answer asked in the FIT's project (AC-010.15); 404 MSG04 for any other answer. */
    void rate(JiraAppPrincipal caller, UUID answerId, JiraAppRatingRequest request);
}
