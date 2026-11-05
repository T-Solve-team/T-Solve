package vn.tsolve.ai.service;

import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;

public interface SolutionAiService {
    SolutionDraft draft(IssueContent issue);
}
