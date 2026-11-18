package vn.tsolve.chatbot.issue.client;

import vn.tsolve.tracker.dto.JiraAccount;

public interface JiraIssueCreator {
    CreatedIssue create(JiraAccount account, NewIssue issue);

    record NewIssue(String projectKey, String summary, String description, String reporterId) {}

    record CreatedIssue(String issueKey, String issueUrl) {}
}
