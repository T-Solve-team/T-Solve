package vn.tsolve.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record KnowledgeDashboardResponse(
        Period period,
        Options options,
        Outcomes outcomes,
        Coverage coverage,
        Feedback feedback,
        Gaps gaps,
        Suggestions suggestions) {
    public record Period(LocalDate from, LocalDate to) {}

    public record Options(List<DepartmentOption> departments, List<ProjectOption> projects) {}

    public record DepartmentOption(UUID departmentId, String name) {}

    public record ProjectOption(UUID projectId, String name, UUID departmentId) {}

    public record Outcomes(
            long approved,
            long rejected,
            long changesRequested,
            Double approvalRate,
            long pendingReview,
            long expired,
            List<DayOutcome> perDay,
            List<ProjectOutcome> perProject) {}

    public record DayOutcome(LocalDate date, long approved, long rejected, long changesRequested) {}

    public record ProjectOutcome(UUID projectId, String name, long approved, long rejected, long changesRequested) {}

    public record Coverage(
            long publishedTickets, long allTickets, Double publishedShare, List<DepartmentCoverage> perDepartment) {}

    public record DepartmentCoverage(
            UUID departmentId, String name, long publishedTickets, long allTickets, Double publishedShare) {}

    public record Feedback(
            long helpful,
            long notHelpful,
            Double helpfulRate,
            List<TicketFeedback> topRated,
            List<TicketFeedback> lowestRated) {}

    public record TicketFeedback(UUID ticketId, String title, long helpful, long notHelpful, double helpfulRate) {}

    public record Gaps(
            List<NoResultSearch> noResultSearches, long chatbotNoSource, List<ChatbotQuestion> chatbotQuestions) {}

    public record NoResultSearch(String query, long count) {}

    public record ChatbotQuestion(String question, long count) {}

    public record Suggestions(long posted, long solved, Double solvedShare) {}
}
