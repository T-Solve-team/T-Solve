package vn.tsolve.jiraapp.service;

import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility;

/** The questions of the "Ask T-Solve" Jira project page (STORY-010). */
public interface JiraAppQuestionService {

    /** Whether the caller may ask; calls no AI (AC-010.2). */
    JiraAskerEligibility eligibility(JiraAppPrincipal caller);

    /**
     * The Knowledge Chatbot's answer for the caller's T-Solve user (AC-010.4). Throws
     * {@code JiraAskerNotLinkedException} before any AI call for an unlinked caller (AC-010.11) and
     * {@code AiUnavailableException} when the AI fails or misses the Jira App deadline (AC-010.8).
     */
    ChatbotAnswerResponse ask(JiraAppPrincipal caller, JiraAppQuestionRequest request);
}
