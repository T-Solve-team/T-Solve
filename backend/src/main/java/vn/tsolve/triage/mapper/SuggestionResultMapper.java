package vn.tsolve.triage.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.tsolve.triage.dto.SuggestionProject;
import vn.tsolve.triage.dto.SuggestionResultDetail;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.dto.SuggestionWikiSource;
import vn.tsolve.triage.entity.SuggestionResultRecord;

@Mapper
public interface SuggestionResultMapper {

    int EXCERPT_LENGTH = 200;

    @Mapping(target = "issueKey", source = "record.jiraIssueKey")
    @Mapping(target = "jiraIssueUrl", source = "jiraIssueUrl")
    @Mapping(target = "project", source = "project")
    @Mapping(target = "createdAt", source = "record.createdAt")
    @Mapping(target = "ticketSourceCount", source = "ticketSourceCount")
    @Mapping(target = "wikiSourceCount", source = "wikiSourceCount")
    @Mapping(target = "suggestedSolutionExcerpt", source = "record.suggestedSolution", qualifiedByName = "excerpt")
    @Mapping(target = "reporterAnswer", source = "record.reporterAnswer")
    SuggestionResultSummary toSummary(
            SuggestionResultRecord record,
            SuggestionProject project,
            String jiraIssueUrl,
            int ticketSourceCount,
            int wikiSourceCount);

    @Mapping(target = "issueKey", source = "record.jiraIssueKey")
    @Mapping(target = "jiraIssueUrl", source = "jiraIssueUrl")
    @Mapping(target = "project", source = "project")
    @Mapping(target = "createdAt", source = "record.createdAt")
    @Mapping(target = "ticketSourceCount", source = "ticketSourceCount")
    @Mapping(target = "wikiSourceCount", expression = "java(wikiSources.size())")
    @Mapping(target = "suggestedSolutionExcerpt", source = "record.suggestedSolution", qualifiedByName = "excerpt")
    @Mapping(target = "reporterAnswer", source = "record.reporterAnswer")
    @Mapping(target = "suggestedSolution", source = "record.suggestedSolution")
    @Mapping(target = "wikiSources", source = "wikiSources")
    SuggestionResultDetail toDetail(
            SuggestionResultRecord record,
            SuggestionProject project,
            String jiraIssueUrl,
            int ticketSourceCount,
            List<SuggestionWikiSource> wikiSources);

    /** Whitespace collapsed, then the first 200 characters, counted in code points so an emoji is never split. */
    @Named("excerpt")
    default String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String plain = text.strip().replaceAll("\\s+", " ");
        if (plain.codePointCount(0, plain.length()) <= EXCERPT_LENGTH) {
            return plain;
        }
        return plain.substring(0, plain.offsetByCodePoints(0, EXCERPT_LENGTH));
    }
}
