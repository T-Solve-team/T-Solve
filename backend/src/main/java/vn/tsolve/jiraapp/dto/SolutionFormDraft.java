package vn.tsolve.jiraapp.dto;

import vn.tsolve.ticket.dto.SolutionSections;

/**
 * What the Solution Form opens with when the user has no Draft: the AI draft ({@code aiDraft} true, AC-002.1) or the
 * issue text in Symptoms ({@code aiDraft} false, shown with MSG67, AC-002.2).
 */
public record SolutionFormDraft(SolutionSections sections, boolean aiDraft) {}
