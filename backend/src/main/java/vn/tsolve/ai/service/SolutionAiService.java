package vn.tsolve.ai.service;

import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.dto.SolutionSections;

public interface SolutionAiService {
    SolutionDraft draft(IssueContent issue);

    SolutionSections rewrite(SolutionSections solution);
}
